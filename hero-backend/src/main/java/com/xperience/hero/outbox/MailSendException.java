package com.xperience.hero.outbox;

/** The provider refused or failed. The drain treats it as one failed attempt (D15). */
public class MailSendException extends RuntimeException {

	public MailSendException(String message, Throwable cause) {
		super(message, cause);
	}
}
