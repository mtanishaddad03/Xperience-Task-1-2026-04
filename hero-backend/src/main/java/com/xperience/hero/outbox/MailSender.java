package com.xperience.hero.outbox;

/**
 * The one place a message leaves the system. A real provider replaces the development sender (D13) here and
 * nowhere else. Returning normally means the provider accepted the message — never that it arrived (RD-1).
 */
public interface MailSender {

	void send(OutgoingMail mail);
}
