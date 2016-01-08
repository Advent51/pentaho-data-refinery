/*!
 * PENTAHO CORPORATION PROPRIETARY AND CONFIDENTIAL
 *
 * Copyright 2002 - 2014 Pentaho Corporation (Pentaho). All rights reserved.
 *
 * NOTICE: All information including source code contained herein is, and
 * remains the sole property of Pentaho and its licensors. The intellectual
 * and technical concepts contained herein are proprietary and confidential
 * to, and are trade secrets of Pentaho and may be covered by U.S. and foreign
 * patents, or patents in process, and are protected by trade secret and
 * copyright laws. The receipt or possession of this source code and/or related
 * information does not convey or imply any rights to reproduce, disclose or
 * distribute its contents, or to manufacture, use, or sell anything that it
 * may describe, in whole or in part. Any reproduction, modification, distribution,
 * or public display of this information without the express written authorization
 * from Pentaho is strictly prohibited and in violation of applicable laws and
 * international treaties. Access to the source code contained herein is strictly
 * prohibited to anyone except those individuals and entities who have executed
 * confidentiality and non-disclosure agreements or other agreements with Pentaho,
 * explicitly covering such access.
 */

package org.pentaho.di.core.refinery.publish.agilebi;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.multipart.BodyPart;
import com.sun.jersey.multipart.FormDataMultiPart;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.pentaho.database.model.DatabaseAccessType;
import org.pentaho.database.model.DatabaseConnection;
import org.pentaho.database.model.IDatabaseType;
import org.pentaho.database.util.DatabaseTypeHelper;
import org.pentaho.di.core.database.DatabaseInterface;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.refinery.publish.model.DataSourceAclModel;
import org.pentaho.di.core.refinery.publish.util.JAXBUtils;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.*;

/**
 * @author Rowell Belen
 */
public class ModelServerPublishTest {

  private ModelServerPublish modelServerPublish;
  private ModelServerPublish modelServerPublishSpy;
  private DatabaseMeta databaseMeta;
  private DatabaseInterface databaseInterface;
  private IDatabaseType databaseType;
  private DatabaseTypeHelper databaseTypeHelper;
  private DatabaseConnection databaseConnection;
  private BiServerConnection connection;
  private Properties attributes;
  private Client client;
  private ClientResponse clientResponse;
  private boolean overwrite;

  @Before
  public void setup() {

    overwrite = false;
    databaseMeta = mock( DatabaseMeta.class );
    connection = new BiServerConnection();
    connection.setUserId( "admin" );
    connection.setPassword( "password" );
    connection.setUrl( "http://localhost:8080/pentaho" );

    client = mock( Client.class );
    databaseInterface = mock( DatabaseInterface.class );
    databaseType = mock( IDatabaseType.class );
    databaseTypeHelper = mock( DatabaseTypeHelper.class );
    databaseConnection = mock( DatabaseConnection.class );
    attributes = mock( Properties.class );
    clientResponse = mock( ClientResponse.class );

    modelServerPublish = new ModelServerPublish();
    modelServerPublishSpy = spy( modelServerPublish );

    // mock responses
    doReturn( client ).when( modelServerPublishSpy ).getClient();

    // inject dependencies
    modelServerPublishSpy.setForceOverwrite( overwrite );
    modelServerPublishSpy.setBiServerConnection( connection );
    modelServerPublishSpy.setDatabaseMeta( databaseMeta );
  }

  @Test
  public void testConnectionnameExists() throws Exception {
    doCallRealMethod().when( modelServerPublishSpy ).getClient();

    DatabaseConnection dbConnection1 = new DatabaseConnection();
    dbConnection1.setName( "test" );
    String json = JAXBUtils.marshallToJson( dbConnection1 );

    // check null connection name
    assertNull( modelServerPublishSpy.connectionNameExists( null ) );

    // check null response
    doReturn( null ).when( modelServerPublishSpy ).httpGet( any( WebResource.Builder.class ) );
    assertNull( modelServerPublishSpy.connectionNameExists( "test" ) );

    // check invalid clientResponse
    doReturn( clientResponse ).when( modelServerPublishSpy ).httpGet( any( WebResource.Builder.class ) );
    assertNull( modelServerPublishSpy.connectionNameExists( "test" ) );

    // check invalid status
    when( clientResponse.getStatus() ).thenReturn( 404 );
    assertNull( modelServerPublishSpy.connectionNameExists( "test" ) );

    // check invalid payload
    when( clientResponse.getStatus() ).thenReturn( 200 );
    assertNull( modelServerPublishSpy.connectionNameExists( "test" ) );

    // valid
    when( clientResponse.getStatus() ).thenReturn( 200 );
    when( clientResponse.getEntity( String.class ) ).thenReturn( json );
    assertNotNull( modelServerPublishSpy.connectionNameExists( "test" ) );
  }

  @Test
  public void testPublishDataSource() throws Exception {

    doReturn( databaseInterface ).when( databaseMeta ).getDatabaseInterface();

    final HashMap<String, String> extraOptions = new HashMap<String, String>();
    final String username = "username";
    final String password = "password";
    final String dbName = "dbName";
    final String dbPort = "dbPort";
    final String hostname = "hostname";

    doReturn( extraOptions ).when( databaseMeta ).getExtraOptions();
    doReturn( username ).when( databaseMeta ).getUsername();
    doReturn( username ).when( databaseMeta ).environmentSubstitute( username );
    doReturn( password ).when( databaseMeta ).getPassword();
    doReturn( password ).when( databaseMeta ).environmentSubstitute( password );
    doReturn( dbName ).when( databaseInterface ).getDatabaseName();
    doReturn( dbName ).when( databaseMeta ).environmentSubstitute( dbName );
    doReturn( dbPort ).when( databaseMeta ).environmentSubstitute( dbPort );
    doReturn( hostname ).when( databaseMeta ).getHostname();
    doReturn( hostname ).when( databaseMeta ).environmentSubstitute( hostname );

    doReturn( attributes ).when( databaseInterface ).getAttributes();
    doReturn( dbPort ).when( attributes ).getProperty( "PORT_NUMBER" );
    doReturn( "Y" ).when( attributes ).getProperty( "FORCE_IDENTIFIERS_TO_LOWERCASE" );
    doReturn( "Y" ).when( attributes ).getProperty( "QUOTE_ALL_FIELDS" );
    doReturn( databaseType ).when( modelServerPublishSpy ).getDatabaseType( databaseInterface );

    modelServerPublishSpy.publishDataSource( true, "id" );
    verify( modelServerPublishSpy ).updateConnection( argThat( new ArgumentMatcher<DatabaseConnection>() {
      @Override public boolean matches( Object o ) {
        DatabaseConnection db = (DatabaseConnection) o;
        return db.getUsername().equals( username )
            && db.getPassword().equals( password )
            && db.getDatabaseName().equals( dbName )
            && db.getDatabasePort().equals( dbPort )
            && db.getHostname().equals( hostname )
            && db.isForcingIdentifiersToLowerCase()
            && db.isQuoteAllFields()
            && db.getAccessType().equals( DatabaseAccessType.NATIVE )
            && db.getExtraOptions().equals( databaseMeta.getExtraOptions() )
            && db.getDatabaseType().equals( databaseType );
      }
    } ), anyBoolean() );

    doReturn( "N" ).when( attributes ).get( anyString() );
    modelServerPublishSpy.publishDataSource( false, "id" );
  }

  @Test
  public void testPublishDataSourceEnvironmentSubstitute() throws Exception {

    doReturn( databaseInterface ).when( databaseMeta ).getDatabaseInterface();
    doReturn( "${USER_NAME}" ).when( databaseMeta ).getUsername();
    doReturn( "${USER_PASSWORD}" ).when( databaseMeta ).getPassword();
    doReturn( "${HOST_NAME}" ).when( databaseMeta ).getHostname();
    doReturn( "SubstitutedUser" ).when( databaseMeta ).environmentSubstitute( "${USER_NAME}" );
    doReturn( "SubstitutedHostName" ).when( databaseMeta ).environmentSubstitute( "${HOST_NAME}" );
    doReturn( "SubstitutedPassword" ).when( databaseMeta ).environmentSubstitute( "${USER_PASSWORD}" );
    doReturn( attributes ).when( databaseInterface ).getAttributes();
    doReturn( "${DB_PORT}" ).when( attributes ).getProperty( anyString() );
    doReturn( "8080" ).when( databaseMeta ).environmentSubstitute( "${DB_PORT}" );
    doReturn( databaseType ).when( modelServerPublishSpy ).getDatabaseType( databaseInterface );

    modelServerPublishSpy.publishDataSource( true, "id" );
    verify( modelServerPublishSpy ).updateConnection( argThat( new ArgumentMatcher<DatabaseConnection>( ) {
        @Override
        public boolean matches( Object o ) {
          DatabaseConnection db = (DatabaseConnection) o;
          return db.getUsername( ).equals( "SubstitutedUser" )
                  && db.getHostname( ).equals( "SubstitutedHostName" )
                  && db.getPassword( ).equals( "SubstitutedPassword" )
                  && db.getDatabasePort( ).equals( "8080" );
        }
      } ), anyBoolean() );

  }



  @Test
  public void testGetDatabaseType() throws Exception {
    doReturn( "" ).when( databaseInterface ).getPluginId();
    assertNull( modelServerPublishSpy.getDatabaseType( databaseInterface ) );
  }

  @Test
  public void testGetClient() throws Exception {

    doCallRealMethod().when( modelServerPublishSpy ).getClient();

    Client client1 = modelServerPublishSpy.getClient();
    Client client2 = modelServerPublishSpy.getClient();
    assertEquals( client1, client2 ); // assert same instance
  }

  @Test
  public void testHttpPost() throws Exception {
    modelServerPublishSpy.httpPost( mock( WebResource.Builder.class ) );
  }

  @Test
  public void testUpdateConnection() throws Exception {

    doCallRealMethod().when( modelServerPublishSpy ).getClient();

    // check null response
    doReturn( null ).when( modelServerPublishSpy ).httpPost( any( WebResource.Builder.class ) );
    boolean success = modelServerPublishSpy.updateConnection( databaseConnection, false );
    assertFalse( success );

    // check invalid clientResponse
    doReturn( clientResponse ).when( modelServerPublishSpy ).httpPost( any( WebResource.Builder.class ) );
    success = modelServerPublishSpy.updateConnection( databaseConnection, false );
    assertFalse( success );

    // check invalid status
    when( clientResponse.getStatus() ).thenReturn( 404 );
    success = modelServerPublishSpy.updateConnection( databaseConnection, false );
    assertFalse( success );

    // valid
    when( clientResponse.getStatus() ).thenReturn( 200 );
    success = modelServerPublishSpy.updateConnection( databaseConnection, false );
    assertTrue( success );
  }

  @Test
  public void testPublishMondrianSchema() throws Exception {

    InputStream mondrianFile = mock( InputStream.class );
    String catalogName = "Catalog";
    String datasourceInfo = "Test";
    WebResource.Builder builder = Mockito.mock( WebResource.Builder.class );

    doCallRealMethod().when( modelServerPublishSpy ).getClient();

    // check null response
    doReturn( null ).when( modelServerPublishSpy ).httpPost( any( WebResource.Builder.class ) );
    int status = modelServerPublishSpy.publishMondrianSchema( mondrianFile, catalogName, datasourceInfo, true );
    assertEquals( ModelServerPublish.PUBLISH_FAILED, status );

    // check invalid clientResponse
    doReturn( builder ).when( modelServerPublishSpy )
        .resourceBuilder(
          argThat( matchResource( "http://localhost:8080/pentaho/plugin/data-access/api/mondrian/postAnalysis" ) ),
          argThat( matchPart(
            "Datasource=Test;retainInlineAnnotations=true", mondrianFile, "Catalog", "true", "true" ) ) );
    doReturn( clientResponse ).when( modelServerPublishSpy ).httpPost( builder );
    status = modelServerPublishSpy.publishMondrianSchema( mondrianFile, catalogName, datasourceInfo, true );
    assertEquals( ModelServerPublish.PUBLISH_FAILED, status );

    // check invalid status
    when( clientResponse.getStatus() ).thenReturn( 404 );
    status = modelServerPublishSpy.publishMondrianSchema( mondrianFile, catalogName, datasourceInfo, true );
    assertEquals( ModelServerPublish.PUBLISH_FAILED, status );

    // valid status, invalid payload
    when( clientResponse.getStatus() ).thenReturn( 200 );
    when( clientResponse.getEntity( String.class ) ).thenReturn( "" );
    status = modelServerPublishSpy.publishMondrianSchema( mondrianFile, catalogName, datasourceInfo, true );
    assertEquals( ModelServerPublish.PUBLISH_FAILED, status );

    // valid status, catalog exists
    when( clientResponse.getEntity( String.class ) ).thenReturn( ModelServerPublish.PUBLISH_CATALOG_EXISTS + "" );
    status = modelServerPublishSpy.publishMondrianSchema( mondrianFile, catalogName, datasourceInfo, true );
    assertEquals( ModelServerPublish.PUBLISH_CATALOG_EXISTS, status );

    // success
    when( clientResponse.getEntity( String.class ) ).thenReturn( ModelServerPublish.PUBLISH_SUCCESS + "" );
    status = modelServerPublishSpy.publishMondrianSchema( mondrianFile, catalogName, datasourceInfo, true );
    assertEquals( ModelServerPublish.PUBLISH_SUCCESS, status );
  }

  private Matcher<FormDataMultiPart> matchPart(
      final String parameters, final Object inputStream, final String catalog,
      final String overwrite, final String xmlaEnabled ) {
    return new BaseMatcher<FormDataMultiPart>() {
      @Override public boolean matches( final Object item ) {
        FormDataMultiPart part = (FormDataMultiPart) item;
        List<BodyPart> bodyParts = part.getBodyParts();
        return bodyParts.size() == 5
          && bodyParts.get( 0 ).getEntity().equals( parameters )
          && bodyParts.get( 1 ).getEntity().equals( inputStream )
          && bodyParts.get( 2 ).getEntity().equals( catalog )
          && bodyParts.get( 3 ).getEntity().equals( overwrite )
          && bodyParts.get( 4 ).getEntity().equals( xmlaEnabled );
      }

      @Override public void describeTo( final Description description ) {

      }
    };
  }

  private Matcher<WebResource> matchResource( final String expectedUri ) {
    return new BaseMatcher<WebResource>() {
      @Override public boolean matches( final Object item ) {
        WebResource resource = (WebResource) item;
        return resource.getURI().toString().equals( expectedUri );
      }

      @Override public void describeTo( final Description description ) {

      }
    };
  }

  @Test
  public void testPublishMetaDataFile() throws Exception {

    InputStream metadataFile = mock( InputStream.class );
    String domainId = "Test";

    doCallRealMethod().when( modelServerPublishSpy ).getClient();

    // check null response
    doReturn( null ).when( modelServerPublishSpy ).httpPut( any( WebResource.Builder.class ) );
    int status = modelServerPublishSpy.publishMetaDataFile( metadataFile, domainId );
    assertEquals( ModelServerPublish.PUBLISH_FAILED, status );

    // check invalid clientResponse
    doReturn( clientResponse ).when( modelServerPublishSpy ).httpPut( any( WebResource.Builder.class ) );
    status = modelServerPublishSpy.publishMetaDataFile( metadataFile, domainId );
    assertEquals( ModelServerPublish.PUBLISH_FAILED, status );

    // check invalid status
    when( clientResponse.getStatus() ).thenReturn( 404 );
    status = modelServerPublishSpy.publishMetaDataFile( metadataFile, domainId );
    assertEquals( ModelServerPublish.PUBLISH_FAILED, status );

    modelServerPublishSpy.setForceOverwrite( true );

    // valid status, invalid payload
    when( clientResponse.getStatus() ).thenReturn( 200 );
    when( clientResponse.getEntity( String.class ) ).thenReturn( "" );
    status = modelServerPublishSpy.publishMetaDataFile( metadataFile, domainId );
    assertEquals( ModelServerPublish.PUBLISH_FAILED, status );

    // success
    when( clientResponse.getEntity( String.class ) ).thenReturn( ModelServerPublish.PUBLISH_SUCCESS + "" );
    status = modelServerPublishSpy.publishMetaDataFile( metadataFile, domainId );
    assertEquals( ModelServerPublish.PUBLISH_SUCCESS, status );

    // valid status, but throw error
    when( clientResponse.getEntity( String.class ) ).thenThrow( new RuntimeException() );
    status = modelServerPublishSpy.publishMetaDataFile( metadataFile, domainId );
    assertEquals( ModelServerPublish.PUBLISH_FAILED, status );
  }


  @Test
  public void testPublishMetaDataFileWithAcl() throws Exception {
    InputStream metadataFile = mock( InputStream.class );
    String domainId = "Test";

    doCallRealMethod().when( modelServerPublishSpy ).getClient();

    DataSourceAclModel aclModel = new DataSourceAclModel();
    aclModel.addUser( "testUser" );
    modelServerPublishSpy.setAclModel( aclModel );

    doReturn( clientResponse ).when( modelServerPublishSpy ).httpPut( any( WebResource.Builder.class ) );
    when( clientResponse.getStatus() ).thenReturn( 200 );
    when( clientResponse.getEntity( String.class ) ).thenReturn( ModelServerPublish.PUBLISH_SUCCESS + "" );
    int status = modelServerPublishSpy.publishMetaDataFile( metadataFile, domainId );
    assertEquals( ModelServerPublish.PUBLISH_SUCCESS, status );
  }


  @Test( expected = IllegalArgumentException.class )
  public void testPublishDsw() throws Exception {

    InputStream metadataFile = mock( InputStream.class );
    String domainId = "Test.xmi";

    doCallRealMethod().when( modelServerPublishSpy ).getClient();

    // check null response
    doReturn( null ).when( modelServerPublishSpy ).httpPut( any( WebResource.Builder.class ) );
    int status = modelServerPublishSpy.publishDsw( metadataFile, domainId );
    assertEquals( ModelServerPublish.PUBLISH_FAILED, status );

    // check invalid clientResponse
    doReturn( clientResponse ).when( modelServerPublishSpy ).httpPut( any( WebResource.Builder.class ) );
    status = modelServerPublishSpy.publishDsw( metadataFile, domainId );
    assertEquals( ModelServerPublish.PUBLISH_FAILED, status );

    // check invalid status
    when( clientResponse.getStatus() ).thenReturn( 404 );
    status = modelServerPublishSpy.publishDsw( metadataFile, domainId );
    assertEquals( ModelServerPublish.PUBLISH_FAILED, status );

    modelServerPublishSpy.setForceOverwrite( true );

    // valid status - 200
    when( clientResponse.getStatus() ).thenReturn( 200 );
    when( clientResponse.getEntity( String.class ) ).thenReturn( ModelServerPublish.PUBLISH_SUCCESS + "" );
    status = modelServerPublishSpy.publishDsw( metadataFile, domainId );
    assertEquals( ModelServerPublish.PUBLISH_SUCCESS, status );

    // valid status - 201
    when( clientResponse.getStatus() ).thenReturn( 201 );
    status = modelServerPublishSpy.publishDsw( metadataFile, domainId );
    assertEquals( ModelServerPublish.PUBLISH_SUCCESS, status );

    // throw exception
    domainId = "Test";
    modelServerPublishSpy.publishDsw( metadataFile, domainId );
  }
}
