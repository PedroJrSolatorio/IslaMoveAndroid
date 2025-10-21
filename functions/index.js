const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

// Cloud Function to process notification queue
exports.processNotification = functions.firestore
    .document('notifications/{notificationId}')
    .onCreate(async (snap, context) => {
        const notification = snap.data();
        console.log('Processing notification:', notification);

        // Skip if already sent
        if (notification.sent) {
            console.log('Notification already sent, skipping');
            return null;
        }

        // Skip if no tokens
        if (!notification.tokens || notification.tokens.length === 0) {
            console.log('No FCM tokens found, marking as failed');
            await snap.ref.update({
                sent: true,
                error: 'No FCM tokens available'
            });
            return null;
        }

        // Create FCM message
        const message = {
            data: {
                ...notification.data,
                click_action: 'FLUTTER_NOTIFICATION_CLICK'
            },
            notification: {
                title: notification.title,
                body: notification.body
            },
            android: {
                notification: {
                    channelId: getChannelId(notification.type),
                    priority: 'high',
                    defaultSound: true,
                    defaultVibrateTimings: true
                }
            },
            tokens: notification.tokens
        };

        try {
            console.log('Sending FCM message to', notification.tokens.length, 'devices');
            const response = await admin.messaging().sendMulticast(message);

            console.log('FCM Response:', {
                successCount: response.successCount,
                failureCount: response.failureCount,
                responses: response.responses.map(r => ({
                    success: r.success,
                    error: r.error?.code
                }))
            });

            // Mark as sent
            await snap.ref.update({
                sent: true,
                sentAt: admin.firestore.FieldValue.serverTimestamp(),
                successCount: response.successCount,
                failureCount: response.failureCount
            });

        } catch (error) {
            console.error('Error sending FCM message:', error);

            // Mark as failed
            await snap.ref.update({
                sent: true,
                error: error.message,
                failedAt: admin.firestore.FieldValue.serverTimestamp()
            });
        }
    });

// Helper function to determine notification channel
function getChannelId(notificationType) {
    switch (notificationType) {
        case 'ride_request':
        case 'ride_cancelled':
            return 'ride_requests'; // High priority channel
        case 'ride_accepted':
        case 'driver_arrived':
        case 'ride_started':
        case 'ride_completed':
            return 'ride_updates';
        default:
            return 'general_notifications';
    }
}