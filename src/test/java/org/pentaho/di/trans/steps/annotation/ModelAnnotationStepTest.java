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
package org.pentaho.di.trans.steps.annotation;

import junit.framework.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import org.pentaho.agilebi.modeler.models.annotations.CreateAttribute;
import org.pentaho.agilebi.modeler.models.annotations.CreateMeasure;
import org.pentaho.agilebi.modeler.models.annotations.ModelAnnotation;
import org.pentaho.agilebi.modeler.models.annotations.ModelAnnotationGroup;
import org.pentaho.agilebi.modeler.models.annotations.ModelAnnotationManager;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.logging.LogLevel;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.value.ValueMetaNumber;
import org.pentaho.di.core.row.value.ValueMetaString;
import org.pentaho.di.job.Job;
import org.pentaho.di.job.entries.build.JobEntryBuildModel;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.metadata.model.concept.types.AggregationType;
import org.pentaho.metastore.api.IMetaStore;

public class ModelAnnotationStepTest {
  @Test
  public void testPutsAnnotationGroupIntoTheExtensionMap() throws Exception {
    StepDataInterface stepDataInterface = new ModelAnnotationData();

    ModelAnnotationStep modelAnnotation = createOneShotStep( stepDataInterface, null, null );
    ModelAnnotationMeta modelAnnotationMeta = new ModelAnnotationMeta();
    CreateAttribute ca1 = new CreateAttribute();
    ca1.setField( "f" );
    ModelAnnotation<?> annotationMock1 = new ModelAnnotation<CreateAttribute>( ca1 );
    ModelAnnotationGroup modelAnnotations = new ModelAnnotationGroup( annotationMock1 );
    modelAnnotationMeta.setModelAnnotations( modelAnnotations );

    modelAnnotation.processRow( modelAnnotationMeta, stepDataInterface );
    ModelAnnotationGroup actualAnnotations =
        (ModelAnnotationGroup) modelAnnotation.getExtensionDataMap().get( JobEntryBuildModel.KEY_MODEL_ANNOTATIONS );
    assertEquals( 1, actualAnnotations.size() );
    assertSame( annotationMock1, actualAnnotations.get( 0 ) );
    CreateAttribute ca2 = new CreateAttribute();
    ca2.setField( "f" );
    ModelAnnotation<?> annotationMock2 = new ModelAnnotation<CreateAttribute>( ca2 );
    modelAnnotations.add( annotationMock2 );
    modelAnnotation.first = true;

    modelAnnotation.processRow( modelAnnotationMeta, stepDataInterface );
    actualAnnotations =
        (ModelAnnotationGroup) modelAnnotation.getExtensionDataMap().get( JobEntryBuildModel.KEY_MODEL_ANNOTATIONS );
    assertEquals( 3, actualAnnotations.size() );
    assertSame( annotationMock1, actualAnnotations.get( 0 ) );
    assertSame( annotationMock1, actualAnnotations.get( 1 ) );
    assertSame( annotationMock2, actualAnnotations.get( 2 ) );
  }

  @Test
  public void testReadsMetaStoreAnnotationGroup() throws Exception {
    final String groupName = "someGroup";

    // metastored annotations
    CreateAttribute ca = new CreateAttribute();
    ca.setField( "f1" );
    CreateMeasure cm = new CreateMeasure();
    cm.setField( "f2" );
    ModelAnnotation<CreateAttribute> a1 = new ModelAnnotation<CreateAttribute>( ca );
    ModelAnnotation<CreateMeasure> a2 = new ModelAnnotation<CreateMeasure>( cm );
    ModelAnnotationGroup modelAnnotations = new ModelAnnotationGroup( a1, a2 );
    modelAnnotations.setName( groupName );

    // 'metastore'
    IMetaStore metaStore = mock( IMetaStore.class );
    final ModelAnnotationManager manager = mock( ModelAnnotationManager.class );
    when( manager.readGroup( groupName, metaStore ) ).thenReturn( modelAnnotations );

    //step
    StepDataInterface stepDataInterface = new ModelAnnotationData();
    ModelAnnotationStep modelAnnotation = spy( createOneShotStep( stepDataInterface, metaStore, manager ) );
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta( new ValueMetaString( "f1" ) );
    rowMeta.addValueMeta( new ValueMetaNumber( "f2" ) );
    when( modelAnnotation.getInputRowMeta() ).thenReturn( rowMeta );

    // set up a linked group in the meta
    ModelAnnotationMeta modelAnnotationMeta = new ModelAnnotationMeta();
    modelAnnotationMeta.setDefault();
    ModelAnnotationGroup linkedGroup = new ModelAnnotationGroup();
    linkedGroup.setName( groupName );
    modelAnnotationMeta.setModelAnnotations( linkedGroup );
    modelAnnotationMeta.setModelAnnotationCategory( groupName );

    // run
    modelAnnotation.processRow( modelAnnotationMeta, stepDataInterface );
    ModelAnnotationGroup actualAnnotations =
        (ModelAnnotationGroup) modelAnnotation.getExtensionDataMap().get( JobEntryBuildModel.KEY_MODEL_ANNOTATIONS );

    for ( int i = 0; i < modelAnnotations.size(); i++ ) {
      assertEquals( modelAnnotations.get( i ), actualAnnotations.get( i ) );
    }
  }

  @Test
  public void testMetaStoreAnnotationGroupNotThere() throws Exception {
    final String groupName = "someGroup";

    // 'metastore'
    IMetaStore metaStore = mock( IMetaStore.class );
    final ModelAnnotationManager manager = mock( ModelAnnotationManager.class );
    when( manager.readGroup( groupName, metaStore ) ).thenReturn( null );

    // step
    StepDataInterface stepDataInterface = new ModelAnnotationData();
    ModelAnnotationStep modelAnnotation = createOneShotStep( stepDataInterface, metaStore, manager );

    // set up a linked group in the meta
    ModelAnnotationMeta modelAnnotationMeta = new ModelAnnotationMeta();
    modelAnnotationMeta.setDefault();
    ModelAnnotationGroup linkedGroup = new ModelAnnotationGroup();
    linkedGroup.setName( groupName );
    modelAnnotationMeta.setModelAnnotations( linkedGroup );
    modelAnnotationMeta.setModelAnnotationCategory( groupName );

    // run
    try {
      modelAnnotation.processRow( modelAnnotationMeta, stepDataInterface );
      Assert.fail();
    } catch ( KettleException e ) {
      //
    }
  }

  @Test
  public void testFailNonNumericMeasure() throws Exception {
    ModelAnnotationGroup group = new ModelAnnotationGroup();

    CreateAttribute attr = new CreateAttribute();
    attr.setName( "a1" );
    attr.setDimension( "d" );
    attr.setHierarchy( "h" );
    attr.setField( "f1" );
    ModelAnnotation<CreateAttribute> attribute = new ModelAnnotation<CreateAttribute>( attr );
    group.add( attribute );

    CreateMeasure cm = new CreateMeasure();
    cm.setName( "measure1" );
    cm.setField( "f2" );
    ModelAnnotation<CreateMeasure> measure = new ModelAnnotation<CreateMeasure>( cm );
    group.add( measure );

    StepDataInterface stepDataInterface = new ModelAnnotationData();
    ModelAnnotationMeta modelAnnotationMeta = new ModelAnnotationMeta();
    modelAnnotationMeta.setDefault();
    modelAnnotationMeta.setModelAnnotations( group );

    // run ok
    ModelAnnotationStep modelAnnotation = spy( createOneShotStep( stepDataInterface, null, null ) );
    RowMeta okMeta = new RowMeta();
    okMeta.addValueMeta( new ValueMetaString( "f1" ) );
    okMeta.addValueMeta( new ValueMetaNumber( "f2" ) );
    when( modelAnnotation.getInputRowMeta() ).thenReturn( okMeta );

    modelAnnotation.processRow( modelAnnotationMeta, stepDataInterface );

    // fail
    RowMeta badMeta = new RowMeta();
    badMeta.addValueMeta( new ValueMetaString( "f1" ) );
    badMeta.addValueMeta( new ValueMetaString( "f2" ) );
    modelAnnotation = spy( createOneShotStep( stepDataInterface, null, null ) );
    when( modelAnnotation.getInputRowMeta() ).thenReturn( badMeta );
    try {
      modelAnnotation.processRow( modelAnnotationMeta, stepDataInterface );
      Assert.fail( "not validated" );
    } catch ( KettleException e ) {
      // ok
    }

    // count is fine for non-numeric
    modelAnnotation = spy( createOneShotStep( stepDataInterface, null, null ) );
    when( modelAnnotation.getInputRowMeta() ).thenReturn( badMeta );
    cm.setAggregateType( AggregationType.COUNT );
    modelAnnotation.processRow( modelAnnotationMeta, stepDataInterface );

    // count distinct is fine for non-numeric
    modelAnnotation = spy( createOneShotStep( stepDataInterface, null, null ) );
    when( modelAnnotation.getInputRowMeta() ).thenReturn( badMeta );
    cm.setAggregateType( AggregationType.COUNT_DISTINCT );
    modelAnnotation.processRow( modelAnnotationMeta, stepDataInterface );

    // fail. does not support maximum
    modelAnnotation = spy( createOneShotStep( stepDataInterface, null, null ) );
    when( modelAnnotation.getInputRowMeta() ).thenReturn( badMeta );
    cm.setAggregateType( AggregationType.MAXIMUM );
    try {
      modelAnnotation.processRow( modelAnnotationMeta, stepDataInterface );
      Assert.fail( "not validated" );
    } catch ( KettleException e ) {
      Assert.assertNotNull( e );
    }
  }

  @Test
  public void testOutputStepIsEmpty() throws Exception {

    // step
    StepDataInterface stepDataInterface = new ModelAnnotationData();
    ModelAnnotationStep modelAnnotation = createOneShotStep( stepDataInterface, null, null );

    // set up a linked group in the meta
    ModelAnnotationMeta modelAnnotationMeta = new ModelAnnotationMeta();
    modelAnnotationMeta.setDefault();
    modelAnnotationMeta.setModelAnnotationCategory( "someGroup" );
    modelAnnotationMeta.setSharedDimension( true );

    // run
    try {
      modelAnnotation.processRow( modelAnnotationMeta, stepDataInterface );
      Assert.fail();
    } catch ( KettleException e ) {
      assertEquals( "Please select a valid data provider step.", e.getMessage().trim() );
    }
  }

  @Test
  public void testOutputStepIsMissing() throws Exception {

    // step
    StepDataInterface stepDataInterface = new ModelAnnotationData();
    ModelAnnotationStep modelAnnotation = createOneShotStep( stepDataInterface, null, null );

    // set up a linked group in the meta
    ModelAnnotationMeta modelAnnotationMeta = new ModelAnnotationMeta();
    modelAnnotationMeta.setDefault();
    modelAnnotationMeta.setTargetOutputStep( "MissingStep" );
    modelAnnotationMeta.setModelAnnotationCategory( "someGroup" );
    modelAnnotationMeta.setSharedDimension( true );

    // run
    try {
      modelAnnotation.processRow( modelAnnotationMeta, stepDataInterface );
      Assert.fail();
    } catch ( KettleException e ) {
      assertEquals( "Please select a valid data provider step.", e.getMessage().trim() );
    }
  }

  private ModelAnnotationStep createOneShotStep( StepDataInterface stepDataInterface, IMetaStore metaStore,
      final ModelAnnotationManager manager ) {
    StepMeta stepMeta = mock( StepMeta.class );
    TransMeta transMeta = mock( TransMeta.class );
    final Trans trans = mock( Trans.class );
    when( stepMeta.getName() ).thenReturn( "someName" );
    when( transMeta.findStep( "someName" ) ).thenReturn( stepMeta );
    Job job = mock( Job.class );
    when( trans.getParentJob() ).thenReturn( job );
    ModelAnnotationStep modelAnnotation = new ModelAnnotationStep( stepMeta, stepDataInterface, 1, transMeta, trans ) {
      @Override public Object[] getRow() throws KettleException {
        return null;
      }

      @Override public Trans getTrans() {
        return trans;
      }

      @Override
      protected ModelAnnotationManager getModelAnnotationsManager( ModelAnnotationMeta meta ) {
        return manager;
      }
    };
    modelAnnotation.setLogLevel( LogLevel.BASIC );
    modelAnnotation.setMetaStore( metaStore );
    when( job.getExtensionDataMap() ).thenReturn( modelAnnotation.getExtensionDataMap() );
    return modelAnnotation;
  }

}
