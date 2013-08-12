package com.lumysoft.lumyd;

import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.oauth.OAuthRequestException;
import com.google.appengine.datanucleus.query.JPACursorHelper;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.Query;

@Api(name = "messageEndpoint",
        namespace = @ApiNamespace(ownerDomain = "lumysoft.com", ownerName = "lumysoft.com", packagePath = "lumydapi"),
        clientIds = {Ids.WEB_CLIENT_ID, Ids.ANDROID_CLIENT_ID},
        audiences = {Ids.ANDROID_AUDIENCE})
public class MessageEndpoint {

    private static final String API_KEY = ""; //TODO: API key

    private static final DeviceInfoEndpoint endpoint = new DeviceInfoEndpoint();

    /**
     * This function returns a list of messages starting with the newest message
     * first and in descending order from there
     *
     * @param cursorString for paging, empty for the first request, subsequent requests can
     *                     use the returned information from an earlier request to fill this
     *                     parameter
     * @param limit        number of results returned for this query
     * @return A collection of MessageData items
     */
    @SuppressWarnings({"unchecked", "unused"})
    @ApiMethod(name = "listMessages")
    public CollectionResponse<MessageData> listMessages(
            @Nullable @Named("cursor") String cursorString,
            @Nullable @Named("limit") Integer limit) {

        EntityManager mgr = null;
        Cursor cursor = null;
        List<MessageData> execute = null;

        try {
            mgr = getEntityManager();
            // query for messages, newest message first
            Query query = mgr
                    .createQuery("select from MessageData as MessageData order by timestamp desc");
            if (cursorString != null && cursorString != "") {
                cursor = Cursor.fromWebSafeString(cursorString);
                query.setHint(JPACursorHelper.CURSOR_HINT, cursor);
            }

            if (limit != null) {
                query.setFirstResult(0);
                query.setMaxResults(limit);
            }

            execute = (List<MessageData>) query.getResultList();
            cursor = JPACursorHelper.getCursor(execute);
            if (cursor != null)
                cursorString = cursor.toWebSafeString();

            // Tight loop for fetching all entities from datastore and accomodate
            // for lazy fetch.
            for (MessageData obj : execute) {
                ;
            }
        } finally {
            mgr.close();
        }

        return CollectionResponse.<MessageData>builder().setItems(execute)
                .setNextPageToken(cursorString).build();
    }

    /**
     * This accepts a message and persists it in the AppEngine datastore, it
     * will also broadcast the message to upto 10 registered android devices
     * via Google Cloud Messaging
     *
     * @param message the entity to be inserted.
     * @return
     * @throws java.io.IOException
     */
    @ApiMethod(name = "sendMessage")
    public void sendMessage(@Named("message") String message,@Named("from") String from,
                            @Named("to") String to, com.google.appengine.api.users.User AuthUser)
            throws OAuthRequestException, IOException {
        if(AuthUser == null){
            throw new OAuthRequestException("Invalid user");
        }
        Sender sender = new Sender(API_KEY);
        // create a MessageData entity with a timestamp of when it was
        // received, and persist it
        MessageData messageObj = new MessageData();
        messageObj.setMessage(message);
        messageObj.setTimestamp(System.currentTimeMillis());
        messageObj.setTo(to);
        messageObj.setFrom(from);
        EntityManager mgr = getEntityManager();
        try {
            mgr.persist(messageObj);
        } finally {
            mgr.close();
        }
        //Sent to the requested user
        doSendViaGcm(message, sender, to);

    }

    /**
     * Sends the message using the Sender object to the registered device.
     *
     * @param message    the message to be sent in the GCM ping to the device.
     * @param sender     the Sender object to be used for ping,
     * @param deviceInfo the registration id of the device.
     * @return Result the result of the ping.
     */
    private static Result doSendViaGcm(String message, Sender sender,
                                       String deviceInfo) throws IOException {
        // Trim message if needed.
        if (message.length() > 1000) {
            message = message.substring(0, 1000) + "[...]";
        }

        // This message object is a Google Cloud Messaging object, it is NOT 
        // related to the MessageData class
        Message msg = new Message.Builder().addData("message", message).build();
        Result result = sender.send(msg, deviceInfo, 5);
        if (result.getMessageId() == null) {
            System.out.println(result.getErrorCodeName());
        }

        return result;
    }

    private static EntityManager getEntityManager() {
        return EMF.get().createEntityManager();
    }
}
