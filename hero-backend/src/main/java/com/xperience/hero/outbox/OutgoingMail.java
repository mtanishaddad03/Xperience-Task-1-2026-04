package com.xperience.hero.outbox;

/**
 * One message handed to the sender.
 *
 * @param link the link, for invitation and management-link messages only — {@code null} on a notice (INV-B13)
 */
public record OutgoingMail(String recipient, MessageKind kind, String link, String eventTitle) {
}
