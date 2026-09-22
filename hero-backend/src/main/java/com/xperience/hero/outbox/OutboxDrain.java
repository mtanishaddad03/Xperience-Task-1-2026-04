package com.xperience.hero.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The Outbox Drain (D8, KD3): the only autonomous execution in the design. It claims a few due messages, hands
 * each to the sender with a timeout, and records the result. It never holds the per-event lock.
 */
@Component
@Slf4j
public class OutboxDrain {

	private final OutboxWork work;
	private final MailSender sender;
	private final HeroProperties properties;
	private final ExecutorService sendPool;

	public OutboxDrain(OutboxWork work, MailSender sender, HeroProperties properties) {
		this.work = work;
		this.sender = sender;
		this.properties = properties;
		// Bounded on purpose: a hung provider occupies one thread, and abandoned sends cannot accumulate.
		this.sendPool = new ThreadPoolExecutor(1, properties.drain().sendThreads(), 30, TimeUnit.SECONDS,
				new SynchronousQueue<>(), runnable -> {
			Thread thread = new Thread(runnable, "outbox-send");
			thread.setDaemon(true);
			return thread;
		});
	}

	/**
	 * One pass. Returns how many messages were attempted, so a caller can drain until nothing is due.
	 */
	public int runOnce() {
		if (work.paused()) {
			return 0;
		}
		work.reclaimStuckMessages();
		List<Long> due = work.due();
		int attempted = 0;
		for (long messageId : due) {
			// Claimed immediately before its own send, never for the whole batch: otherwise the last message of a
			// slow batch would sit in sending past the reclaim threshold and be sent twice.
			Optional<SendJob> claimed = work.claimAndPrepare(messageId);
			if (claimed.isEmpty()) {
				continue; // taken by nobody, or skipped because it is no longer true
			}
			attempted++;
			handOver(claimed.get());
		}
		return attempted;
	}

	private void handOver(SendJob job) {
		Future<?> sending = null;
		try {
			sending = sendPool.submit(() -> sender.send(job.mail()));
			sending.get(properties.drain().sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
			work.recordSent(job);
		}
		catch (TimeoutException e) {
			sending.cancel(true);
			work.recordFailure(job, "the provider did not answer within the send timeout");
		}
		catch (RejectedExecutionException e) {
			work.recordFailure(job, "no send thread was free");
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			work.recordFailure(job, "interrupted");
		}
		catch (Exception e) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			work.recordFailure(job, cause.getMessage());
		}
	}
}
