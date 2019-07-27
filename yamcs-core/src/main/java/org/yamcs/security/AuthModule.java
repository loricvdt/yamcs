package org.yamcs.security;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.http.AuthHandler;

/**
 * Interface implemented by the Authentication and Authorization modules.
 * 
 * The AuthModule has to associate to each user AuthenticationInfo that may contain contextual security properties.
 * Based on this {@link AuthHandler} will generate a JWT token which is passed between the client and the server with
 * each request.
 * 
 * @author nm
 */
public interface AuthModule {

    /**
     * Returns the valid configuration of the input args of this AuthModule.
     * 
     * @return the argument specification.
     */
    Spec getSpec();

    /**
     * Initialize this AuthModule.
     * 
     * @param args
     *            The configured arguments for this AuthModule. If {@link #getSpec()} is implemented then this contains
     *            the arguments after being validated (including any defaults).
     * @throws InitException
     *             When something goes wrong during the execution of this method.
     */
    void init(YConfiguration args) throws InitException;

    /**
     * Identify the subject based on the given information.
     * 
     * @param token
     * @return an info object containing the principal of the subject, or <tt>null</tt> if the login failed
     */
    AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException;

    /**
     * Retrieve access control information based on the given AuthenticationInfo. This AuthenticationInfo may have been
     * generated by a different AuthModule.
     * 
     * @return an info object containing role/privilege information of the subject
     */
    AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) throws AuthorizationException;

    /**
     * Verify if the previously authenticated user is (still) valid
     * 
     * @param user
     *            user to be verified
     * @return true if the user is valid, false otherwise
     * 
     */
    boolean verifyValidity(User user);
}
