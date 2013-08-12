package com.lumysoft.lumyd;

import com.beoui.geocell.GeocellManager;
import com.beoui.geocell.model.GeocellQuery;
import com.beoui.geocell.model.Point;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.appengine.api.oauth.OAuthRequestException;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.persistence.EntityManager;

@Api(name = "userendpoint",
     namespace = @ApiNamespace(ownerDomain = "lumysoft.com", ownerName = "lumysoft.com", packagePath = "lumydapi"),
     clientIds = {Ids.WEB_CLIENT_ID, Ids.ANDROID_CLIENT_ID},
     audiences = {Ids.ANDROID_AUDIENCE})
public class UserEndpoint {

    /**
     * This method lists all the entities inserted in datastore.
     *
     * @return A CollectionResponse class containing the list of all entities
     * persisted.
     */
    @SuppressWarnings({"unchecked", "unused"})
    @ApiMethod(name = "listUser", path = "list_users", httpMethod = ApiMethod.HttpMethod.POST)
    public CollectionResponse<User> listUser(
            @Nullable @Named("limit") Integer limit, // 100 by default
            @Named("distance") int distance, // in meters. 0 for no maximum distance
            Point location) {

        EntityManager mgr = null;
        List<User> execute = null;

        try {
            mgr = getEntityManager();
            GeocellQuery baseQuery = new GeocellQuery("select from User as User");

            if (limit == null) {
                limit = 100;
            }
            execute = GeocellManager.proximitySearch(location, limit, distance, User.class, baseQuery, mgr, 1);
            // Tight loop for fetching all entities from datastore and accomodate
            // for lazy fetch.
            for (User obj : execute) ;
        } finally {
            if (mgr != null) {
                mgr.close();
            }
        }

        return CollectionResponse.<User>builder()
                .setItems(execute)
                .build();
    }

    /**
     * This method gets the entity having primary key id. It uses HTTP GET method.
     *
     * @param Id the primary key of the java bean.
     * @return The entity with primary key id.
     */
    @ApiMethod(name = "getUser", path="user")
    public com.google.appengine.api.users.User getUser(@Named("Id") String Id) {
        EntityManager mgr = getEntityManager();
        com.google.appengine.api.users.User user = null;
        try {
            user = mgr.find(com.google.appengine.api.users.User.class, Id);
        } finally {
            mgr.close();
        }
        return user;
    }

    /**
     * This inserts a new entity into App Engine datastore.
     * It uses HTTP POST method.
     *
     * @param user the entity to be inserted.
     * @return The inserted entity.
     */
    @ApiMethod(name = "insertUser", path = "insert_user")
    public User insertUser(User user, com.google.appengine.api.users.User AuthUser) throws
            OAuthRequestException, IOException {
        EntityManager mgr = getEntityManager();
        if(AuthUser == null){
            throw new OAuthRequestException("Invalid user. Unauthorized user");
        } else if(user.getSourceDevice() == "") {
            throw new IOException("Invalid user. Id not set");
        }
        try {
            Point p = new Point(user.getLatitude(), user.getLongitude());
            List<String> cells = GeocellManager.generateGeoCell(p);
            user.setGeoCellsData(cells);
            user.setTimestamp(System.currentTimeMillis()/1000);
            mgr.persist(user);
        } finally {
            mgr.close();
        }
        return user;
    }

    private static EntityManager getEntityManager() {
        return EMF.get().createEntityManager();
    }
}
