package com.xperience.hero.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The development sender (D13): no provider is chosen (T4), so messages are written to the console and nothing
 * leaves the machine. It prints working links by design — acceptable only because no real guest uses it.
 */
@Component
@Slf4j
public class ConsoleMailSender implements MailSender {

	@Override
	public void send(OutgoingMail mail) {
		log.info("[DEV MAIL] to={} kind={} event=\"{}\" link={}",
				mail.recipient(), mail.kind(), mail.eventTitle(), mail.link() == null ? "(none)" : mail.link());
	}
}
