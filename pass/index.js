/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

const functions = require("firebase-functions");
const {onCall} = require("firebase-functions/v2/https");
const {getFirestore} = require("firebase-admin/firestore");
const {getAuth} = require("firebase-admin/auth");
const logger = require("firebase-functions/logger");

// Initialize Firebase Admin
const admin = require("firebase-admin");
admin.initializeApp();

const db = getFirestore();
const auth = getAuth();

// For cost control, you can set the maximum number of containers that can be
// running at the same time. This helps mitigate the impact of unexpected
// traffic spikes by instead downgrading performance. This limit is a
// per-function limit. You can override the limit for each function using the
// `maxInstances` option in the function's options, e.g.
// `onRequest({ maxInstances: 5 }, (req, res) => { ... })`.
// NOTE: setGlobalOptions does not apply to functions using the v1 API. V1
// functions should each use functions.runWith({ maxInstances: 10 }) instead.
// In the v1 API, each function can only serve one request per container, so
// this will be the maximum concurrent request count.
// Max instances configuration removed - using default

/**
 * Update user password in Firebase Auth when changed in Firestore
 * This function is called by the admin panel when updating user passwords
 */
exports.updateUserPassword = onCall(async (request) => {
  try {
    const {uid, newPassword, adminId} = request.data;

    // Validate required fields
    if (!uid || !newPassword || !adminId) {
      throw new Error("Missing required fields: uid, newPassword, adminId");
    }

    // Verify the admin exists and has proper permissions
    const adminDoc = await db.collection("users").doc(adminId).get();
    if (!adminDoc.exists) {
      throw new Error("Admin not found");
    }

    const adminData = adminDoc.data();
    if (adminData.userType !== "ADMIN") {
      throw new Error("Unauthorized: User is not an admin");
    }

    // Update the user's password in Firebase Auth
    await auth.updateUser(uid, {
      password: newPassword,
    });

    // Log the action for security audit
    await db.collection("security_logs").add({
      action: "password_update",
      targetUserId: uid,
      performedBy: adminId,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      details: "Password updated by admin",
    });

    logger.info(`Password updated for user ${uid} by admin ${adminId}`);

    return {success: true, message: "Password updated successfully"};
  } catch (error) {
    logger.error("Error updating user password:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});

/**
 * Hard delete user - Completely remove user from Firebase Auth and Firestore
 * This function permanently deletes the user account
 */
exports.deleteUser = onCall(async (request) => {
  try {
    const {uid, adminId} = request.data;

    // Validate required fields
    if (!uid || !adminId) {
      throw new Error("Missing required fields: uid, adminId");
    }

    // Verify the admin exists and has proper permissions
    const adminDoc = await db.collection("users").doc(adminId).get();
    if (!adminDoc.exists) {
      throw new Error("Admin not found");
    }

    const adminData = adminDoc.data();
    if (adminData.userType !== "ADMIN") {
      throw new Error("Unauthorized: User is not an admin");
    }

    // Get user data for logging before deletion
    const userDoc = await db.collection("users").doc(uid).get();
    const userData = userDoc.exists ? userDoc.data() : null;

    // Delete user document from Firestore
    await db.collection("users").doc(uid).delete();

    // Delete user from Firebase Auth
    try {
      await auth.deleteUser(uid);
    } catch (authError) {
      // If user doesn't exist in Auth, continue with Firestore deletion
      logger.warn(`User ${uid} not found in Firebase Auth, continuing with Firestore deletion`);
    }

    // Log the action for security audit
    await db.collection("security_logs").add({
      action: "user_hard_delete",
      targetUserId: uid,
      performedBy: adminId,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      details: `User account permanently deleted by admin. User data: ${JSON.stringify(userData)}`,
    });
    logger.info(`User ${uid} permanently deleted by admin ${adminId}`);
    return {success: true, message: "User account permanently deleted"};
  } catch (error) {
    logger.error("Error deleting user:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});

/**
 * Legacy soft delete function - deprecated
 * This function is kept temporarily for cleanup
 */
exports.softDeleteUser = onCall(async (request) => {
  throw new functions.https.HttpsError("deprecated", "This function has been replaced by hard delete. Use deleteUser instead.");
});

/**
 * Legacy restore function - deprecated
 * This function is kept temporarily for cleanup
 */
exports.restoreUser = onCall(async (request) => {
  throw new functions.https.HttpsError("deprecated", "This function has been deprecated. User restoration is not available with hard delete.");
});

/**
 * Get user authentication status
 * Returns whether the user is active in Firebase Auth and Firestore
 */
exports.getUserAuthStatus = onCall(async (request) => {
  try {
    const {uid} = request.data;

    if (!uid) {
      throw new Error("Missing required field: uid");
    }

    // Get Firestore user data
    const userDoc = await db.collection("users").doc(uid).get();
    const firestoreData = userDoc.exists ? userDoc.data() : null;

    // Get Firebase Auth user data
    let authData = null;
    try {
      authData = await auth.getUser(uid);
    } catch (error) {
      // User might not exist in Auth
      logger.warn(`User ${uid} not found in Firebase Auth`);
    }

    return {
      firestore: {
        exists: userDoc.exists,
        isActive: (firestoreData && firestoreData.isActive) || true,
        isDeleted: (firestoreData && firestoreData.isDeleted) || false,
        deletedAt: (firestoreData && firestoreData.deletedAt) || null,
      },
      auth: {
        exists: authData !== null,
        disabled: (authData && authData.disabled) || false,
        emailVerified: (authData && authData.emailVerified) || false,
      },
    };
  } catch (error) {
    logger.error("Error getting user auth status:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});