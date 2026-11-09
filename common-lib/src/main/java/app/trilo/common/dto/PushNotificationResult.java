package app.trilo.common.dto;

/**
 * Summary of a push send request.
 *
 * @param targetedDevices number of devices that matched the target users
 * @param sent number of devices a push was dispatched to immediately
 * @param deferred number of users whose push was deferred to morning delivery
 * @param failed number of devices that failed to send
 */
public record PushNotificationResult(int targetedDevices, int sent, int deferred, int failed) {}
