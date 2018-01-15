/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License
 */

package org.wso2.carbon.identity.openidconnect.util;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.testng.Assert;
import org.wso2.carbon.identity.core.util.IdentityDatabaseUtil;
import org.wso2.carbon.identity.oauth.dao.SQLQueries;
import org.wso2.carbon.identity.oauth2.RequestObjectException;
import org.wso2.carbon.identity.oauth2.authcontext.JWTTokenGenerator;
import org.wso2.carbon.user.core.UserCoreConstants;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

public class TestUtils {

    /**
     * Return a JWT string with provided info, and default time
     *
     * @param issuer
     * @param subject
     * @param jti
     * @param audience
     * @param algorythm
     * @param privateKey
     * @param notBeforeMillis
     * @return
     * @throws org.wso2.carbon.identity.oauth2.RequestObjectException
     */
    public static String buildJWT(String issuer, String subject, String jti, String audience, String algorythm,
                                  Key privateKey, long notBeforeMillis, Map<String,Object> claims)
            throws RequestObjectException {

        JWTClaimsSet jwtClaimsSet = getJwtClaimsSet(issuer, subject, jti, audience, notBeforeMillis, claims);
        if (JWSAlgorithm.NONE.getName().equals(algorythm)) {
            return new PlainJWT(jwtClaimsSet).serialize();
        }

        return signJWTWithRSA(jwtClaimsSet, privateKey);
    }

    private static JWTClaimsSet getJwtClaimsSet(String issuer, String subject, String jti, String audience, long notBeforeMillis, Map<String, Object> claims) {
        long lifetimeInMillis = 3600 * 1000;
        long curTimeInMillis = Calendar.getInstance().getTimeInMillis();

        // Set claims to jwt token.
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet();
        jwtClaimsSet.setIssuer(issuer);
        jwtClaimsSet.setSubject(subject);
        jwtClaimsSet.setAudience(Arrays.asList(audience));
        jwtClaimsSet.setJWTID(jti);
        jwtClaimsSet.setExpirationTime(new Date(curTimeInMillis + lifetimeInMillis));
        jwtClaimsSet.setIssueTime(new Date(curTimeInMillis));

        if (notBeforeMillis > 0) {
            jwtClaimsSet.setNotBeforeTime(new Date(curTimeInMillis + notBeforeMillis));
        }
        if (claims != null && claims.isEmpty()) {
            for (Map.Entry entry : claims.entrySet()) {
                jwtClaimsSet.setClaim(entry.getKey().toString(), entry.getValue());
            }
        }
        return jwtClaimsSet;
    }

    public static String buildJWT(String issuer, String subject, String jti, String audience, String algorythm,
                                  Key privateKey, long notBeforeMillis, long lifetimeInMillis, long issuedTime)
            throws RequestObjectException {

        long curTimeInMillis = Calendar.getInstance().getTimeInMillis();
        if (issuedTime < 0) {
            issuedTime = curTimeInMillis;
        }
        if (lifetimeInMillis <= 0) {
            lifetimeInMillis = 3600 * 1000;
        }
        // Set claims to jwt token.
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet();
        jwtClaimsSet.setIssuer(issuer);
        jwtClaimsSet.setSubject(subject);
        jwtClaimsSet.setAudience(Arrays.asList(audience));
        jwtClaimsSet.setJWTID(jti);
        jwtClaimsSet.setExpirationTime(new Date(issuedTime + lifetimeInMillis));
        jwtClaimsSet.setIssueTime(new Date(issuedTime));

        if (notBeforeMillis > 0) {
            jwtClaimsSet.setNotBeforeTime(new Date(issuedTime + notBeforeMillis));
        }
        if (JWSAlgorithm.NONE.getName().equals(algorythm)) {
            return new PlainJWT(jwtClaimsSet).serialize();
        }

        return signJWTWithRSA(jwtClaimsSet, privateKey);
    }

    /**
     * sign JWT token from RSA algorithm
     *
     * @param jwtClaimsSet contains JWT body
     * @param privateKey
     * @return signed JWT token
     * @throws RequestObjectException
     */
    public static String signJWTWithRSA(JWTClaimsSet jwtClaimsSet, Key privateKey)
            throws RequestObjectException {
        SignedJWT signedJWT = getSignedJWT(jwtClaimsSet, (RSAPrivateKey) privateKey);
        return signedJWT.serialize();
    }

    private static SignedJWT getSignedJWT(JWTClaimsSet jwtClaimsSet, RSAPrivateKey privateKey) throws RequestObjectException {
        try {
        JWSSigner signer = new RSASSASigner(privateKey);
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), jwtClaimsSet);
        signedJWT.sign(signer);
        return signedJWT;
        } catch (JOSEException e) {
            throw new RequestObjectException("error_signing_jwt","Error occurred while signing JWT.");
        }
    }

    /**
     * Create a auth-app in the given tenant with given consumerKey and consumerSecreat
     *
     * @param consumerKey
     * @param consumerSecret
     * @param tenantId
     */
    public static void createApplication(String consumerKey, String consumerSecret, int tenantId) {
        try (Connection connection = IdentityDatabaseUtil.getDBConnection();
             PreparedStatement prepStmt = connection.prepareStatement(SQLQueries.OAuthAppDAOSQLQueries.ADD_OAUTH_APP)) {
            prepStmt.setString(1, consumerKey);
            prepStmt.setString(2, consumerSecret);
            prepStmt.setString(3, "testUser");
            prepStmt.setInt(4, tenantId);
            prepStmt.setString(5, UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME);
            prepStmt.setString(6, "oauth2-app");
            prepStmt.setString(7, "OAuth-2.0");
            prepStmt.setString(8, "some-call-back");
            prepStmt.setString(9, "refresh_token urn:ietf:params:oauth:grant-type:saml2-bearer implicit password " +
                    "client_credentials iwa:ntlm authorization_code urn:ietf:params:oauth:grant-type:jwt-bearer");
            prepStmt.execute();
            connection.commit();
        } catch (SQLException e) {
            Assert.fail("Unable to add Oauth application.");
            //throw new TestNGException("Unable to add Oauth application.");
        }
    }

    /**
     * Read Keystore from the file identified by given keystorename, password
     *
     * @param keystoreName
     * @param password
     * @param home
     * @return
     * @throws Exception
     */
    public static KeyStore getKeyStoreFromFile(String keystoreName, String password,
                                               String home) throws Exception {
        Path tenantKeystorePath = Paths.get(home, "repository",
                "resources", "security", keystoreName);
        FileInputStream file = new FileInputStream(tenantKeystorePath.toString());
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(file, password.toCharArray());
        return keystore;
    }


    public static String buildJWE(String issuer, String subject, String jti, String audience, String algorythm,
                                  Key privateKey,Key publicKey, long notBeforeMillis, Map<String,
            Object> claims) throws RequestObjectException {
        JWTClaimsSet jwtClaimsSet = getJwtClaimsSet(issuer, subject, jti, audience, notBeforeMillis, claims);

        if (JWSAlgorithm.NONE.getName().equals(algorythm)) {
            return getEncryptedJWT((RSAPublicKey) publicKey, jwtClaimsSet);
        } else {
            return getSignedAndEncryptedJWT(publicKey, (RSAPrivateKey) privateKey, jwtClaimsSet);

        }


    }

    private static String getSignedAndEncryptedJWT(Key publicKey, RSAPrivateKey privateKey, JWTClaimsSet jwtClaimsSet) throws RequestObjectException {
        SignedJWT signedJWT = getSignedJWT(jwtClaimsSet, privateKey);
        // Create JWE object with signed JWT as payload
        JWEHeader jweHeader = new JWEHeader(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM);
//        jweHeader.setContentType("JWT");
        JWEObject jweObject = new JWEObject(jweHeader, new Payload(signedJWT.serialize()));
        // Perform encryption
        try {
            jweObject.encrypt(new RSAEncrypter((RSAPublicKey) publicKey));
            return jweObject.serialize();
        } catch (JOSEException e) {
            System.out.print(e);
            e.printStackTrace();
            throw new RequestObjectException("error_building_jwd","Error occurred while creating JWE.");
        }
    }

    private static String getEncryptedJWT(RSAPublicKey publicKey, JWTClaimsSet jwtClaimsSet) throws
            RequestObjectException {
        // Request JWT encrypted with RSA-OAEP-256 and 128-bit AES/GCM
        JWEHeader header = new JWEHeader(JWEAlgorithm.RSA_OAEP, EncryptionMethod.A128GCM);

        // Create the encrypted JWT object
        EncryptedJWT jwt = new EncryptedJWT(header, jwtClaimsSet);

        try {
        // Create an encrypter with the specified public RSA key
            RSAEncrypter encrypter = new RSAEncrypter(publicKey);
            // Do the actual encryption
            jwt.encrypt(encrypter);
        } catch (JOSEException e) {
            throw new RequestObjectException("error_building_jwd","Error occurred while creating JWE JWT.");

        }
        return jwt.serialize();
    }
}