package com.xperience.hero.api;

import com.xperience.hero.outbox.MailSendException;
import com.xperience.hero.outbox.MailSender;
import com.xperience.hero.outbox.MessageKind;
import com.xperience.hero.outbox.OutgoingMail;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/** Stands in for the development sender (D13): records what was handed over, and can fail or hang on demand. */
public class RecordingMailSender implements MailSender {

	private final List<OutgoingMail> sent = new CopyOnWriteArrayList<>();
	private final Set<String> failing = ConcurrentHashMap.newKeySet();
	private final Set<String> hanging = ConcurrentHashMap.newKeySet();
	private final java.util.Map<String, java.time.Duration> delays = new ConcurrentHashMap<>();
	private final CountDownLatch releaseHung = new CountDownLatch(1);

	@Override
	public void send(OutgoingMail mail) {
		java.time.Duration delay = delays.get(mail.recipient());
		if (delay != null) {
			try {
				Thread.sleep(delay.toMillis());
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new MailSendException("interrupted", e);
			}
		}
		if (hanging.contains(mail.recipient())) {
			try {
				releaseHung.await();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new MailSendException("interrupted", e);
			}
		}
		if (failing.contains(mail.recipient())) {
			// Recorded first: a provider can accept a message and still report a failure.
			throw new MailSendException("provider refused " + mail.recipient(), null);
		}
		sent.add(mail);
	}

	public void reset() {
		sent.clear();
		failing.clear();
		hanging.clear();
		delays.clear();
	}

	public void failFor(String recipient) {
		failing.add(recipient);
	}

	public void stopFailingFor(String recipient) {
		failing.remove(recipient);
	}

	public void hangFor(String recipient) {
		hanging.add(recipient);
	}

	public void delayFor(String recipient, java.time.Duration delay) {
		delays.put(recipient, delay);
	}

	public List<OutgoingMail> sent() {
		return List.copyOf(sent);
	}

	public List<OutgoingMail> sentOfKind(MessageKind kind) {
		return sent.stream().filter(m -> m.kind() == kind).toList();
	}

	public OutgoingMail lastTo(String recipient) {
		return sent.stream().filter(m -> m.recipient().equals(recipient)).reduce((a, b) -> b).orElseThrow();
	}

	public long countTo(String recipient) {
		return sent.stream().filter(m -> m.recipient().equals(recipient)).count();
	}
}
