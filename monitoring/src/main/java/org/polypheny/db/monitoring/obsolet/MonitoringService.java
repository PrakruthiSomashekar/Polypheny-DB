/*
 * Copyright 2019-2021 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.monitoring.obsolet;


import lombok.extern.slf4j.Slf4j;
import org.mapdb.DBException.SerializationError;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogTable;
import org.polypheny.db.information.InformationGroup;
import org.polypheny.db.information.InformationManager;
import org.polypheny.db.information.InformationPage;
import org.polypheny.db.information.InformationTable;
import org.polypheny.db.monitoring.obsolet.storage.BackendConnector;
import org.polypheny.db.monitoring.obsolet.storage.InfluxBackendConnector;
import org.polypheny.db.monitoring.obsolet.storage.SimpleBackendConnector;
import org.polypheny.db.monitoring.obsolet.subscriber.Subscriber;
import org.polypheny.db.monitoring.obsolet.subscriber.SubscriptionTopic;
import org.polypheny.db.prepare.RelOptTableImpl;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.schema.LogicalTable;
import org.polypheny.db.util.background.BackgroundTask.TaskPriority;
import org.polypheny.db.util.background.BackgroundTask.TaskSchedulingType;
import org.polypheny.db.util.background.BackgroundTaskManager;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

//ToDo add some kind of configuration which can for one decide on which backend to select, if we might have severall like
// * InfluxDB
// * File
// * map db
// * etc

// Todo eventual MOM outsourced to other hosts
//ToDO think about managing retention times to save storage
@Slf4j
public class MonitoringService {

    public static final MonitoringService INSTANCE = new MonitoringService();

    private static final long serialVersionUID = 2312903251112906177L;

    // Configurable via central CONFIG
    private final String MONITORING_BACKEND = "simple"; //InfluxDB
        // number of elements beeing processed from the queue to the backend per "batch"
    private final int QUEUE_PROCESSING_ELEMENTS = 50;
    //TODO: Add to central configuration
    private boolean isPeristend = true;

    private BackendConnector backendConnector;
    BackendConnectorFactory backendConnectorFactory = new BackendConnectorFactory();


    //handles subscriptions and message delivery
    private EventBroker broker = new EventBroker();


    // private static final String FILE_PATH = "queueMapDB";
    // private static DB queueDb;

    private static final AtomicLong queueIdBuilder = new AtomicLong();

    // private static BTreeMap<Long, MonitorEvent> eventQueue;
    private final TreeMap<Long, MonitorEvent> eventQueue = new TreeMap<>();


    private InformationPage informationPage;
    private InformationGroup informationGroupOverview;
    private InformationTable queueOverviewTable;
    private InformationGroup informationSubOverview;
    private InformationTable activeSubscriptionTable;


    public MonitoringService(){

        initializeMonitoringBackend();

        initPersistentDBQueue();

        initializeInformationPage();


        // Background Task tp
        String taskId = BackgroundTaskManager.INSTANCE.registerTask(
                this::processEventsInQueue,
                "Send monitoring events from queue to backend subscribers",
                TaskPriority.LOW,
                TaskSchedulingType.EVERY_TEN_SECONDS
        );
    }


    private void initializeInformationPage(){
        //Initialize Information Page
        informationPage = new InformationPage( "Workload Monitoring" );
        informationPage.fullWidth();
        informationGroupOverview = new InformationGroup( informationPage, "Queue Overview" );
        informationGroupOverview.setRefreshFunction( this::updateQueueInformationTable );

        informationSubOverview = new InformationGroup( informationPage, "Active Subscriptions" );


        InformationManager im = InformationManager.getInstance();
        im.addPage( informationPage );
        im.addGroup( informationGroupOverview );
        im.addGroup( informationSubOverview );

        queueOverviewTable = new InformationTable(
                informationGroupOverview,
                Arrays.asList( "Queue ID", "STMT", "Description", " Recorded Timestamp", "Field Names") );
        im.registerInformation( queueOverviewTable );

        activeSubscriptionTable = new InformationTable( informationSubOverview,
                Arrays.asList( "Subscriber", "Type", "Object Id","Description", " Subscription Start", "Persistent") );
        im.registerInformation( activeSubscriptionTable );
    }

    private void initPersistentDBQueue() {
        /*if ( queueDb != null ) {
            queueDb.close();
        }
        synchronized ( this ) {

            File folder = FileSystemManager.getInstance().registerNewFolder( "monitoring" );

            queueDb = DBMaker.fileDB( new File( folder, this.FILE_PATH ) )
                            .closeOnJvmShutdown()
                            .transactionEnable()
                            .fileMmapEnableIfSupported()
                            .fileMmapPreclearDisable()
                            .make();

            queueDb.getStore().fileLoad();

            eventQueue = treeMap( "queue", Serializer.LONG, Serializer.JAVA ).createOrOpen();
            */
            try{

                restoreIdBuilder(eventQueue, queueIdBuilder);
            } catch (SerializationError e ) {
                log.error( "!!!!!!!!!!! Error while restoring the monitoring queue !!!!!!!!!!!" );
                log.error( "This usually means that there have been changes to the internal structure of the monitoring queue with the last update of Polypheny-DB." );
                log.error( "To fix this, you must reset the catalog. To do this, please ..." );
                System.exit( 1 );
            }


       // }

    }

    private void restoreIdBuilder( Map<Long, ?> map, AtomicLong idBuilder ) {
        if ( !map.isEmpty() ) {
            idBuilder.set( Collections.max( map.keySet() ) + 1 );
        }
    }


    /**
     * This method should be used to add new items to backend
     * it should be invoked directly as it represents the face to other processes.
     *
     * It is backend agnostic and makes sure to parse and extract all necessary information
     * which should be added to the backend
     *
     * @param event to add to the queue which will registered as a new monitoring metric
     */
    public void addWorkloadEventToQueue(MonitorEvent event){

        long id = queueIdBuilder.getAndIncrement();

        //Add event to persitent queue
        synchronized ( this ) {
            eventQueue.put( id, event );
        }
    }


    private MonitorEvent processRelNode(RelNode node, MonitorEvent currentEvent){
        for ( int i = 0; i < node.getInputs().size(); i++ ) {
            processRelNode(node.getInput( i ),currentEvent);
        }
        System.out.println(node);
        if ( node.getTable() != null ){
            System.out.println("FOUND TABLE : " + node.getTable());
            currentEvent.setTable( node.getTable() );
        }
        return currentEvent;
    }


    //ASYNC Queue processing FIFO
    //ToDO mabye add more intelligent scheduling later on or introduce config to change processing
    //Will be executed every 5seconds due to Background Task Manager and checks the queue and then asyncronously writes them to backend
    public void processEventsInQueue(){

        long currentKey = -1;
        for ( int i = 0; i < this.QUEUE_PROCESSING_ELEMENTS; i++ ) {

            try {
                currentKey = eventQueue.firstEntry().getKey();
            }catch ( NullPointerException e ){
                System.out.println("QUEUE is empty...skipping now");
                break;
            }

            //Temporary testing //ToDO outsource to separate method
            MonitorEvent procEvent = eventQueue.get( currentKey );

            procEvent = processRelNode( procEvent.getRouted().rel, procEvent );



            System.out.println("\n\n\n\n");

             if ( procEvent.getTable() != null ) {
              //extract information from table
                RelOptTableImpl table = (RelOptTableImpl) procEvent.getTable();

                 System.out.println(table.getTable());


                 if ( table.getTable() instanceof LogicalTable ) {
                     LogicalTable t = ((LogicalTable) table.getTable());
                     // Get placements of this table
                     CatalogTable catalogTable = Catalog.getInstance().getTable( t.getTableId() );
                     System.out.println( "Added Event for table: " + catalogTable.name );
                 }else {
                     log.info( "Unexpected table. Only logical tables expected here! {}", table.getTable() );
                     //throw new RuntimeException( "Unexpected table. Only logical tables expected here!" );
                 }
            }
            else{
                log.info(" Unusual processing {} ",  procEvent.getRouted().rel );
                //throw new RuntimeException( "Unexpected operator!" );
            }

            synchronized ( this ) {
                if ( backendConnector.writeStatisticEvent( currentKey, eventQueue.get( currentKey ) ) ){
                    //Remove processed entry from queue
                    //TODO reenable eventQueue.remove( currentKey );
                    log.debug( "Processed Event in Queue: '{}'.", currentKey );
                }
                else{
                    log.info( "Problem writing Event in Queue: '{}'. Skipping entry.", currentKey );
                    continue;
                }
            }
            //Todo Send Event to Broker once the event has been persisted at central monitoring backend configured in config
            broker.processEvent( procEvent );

            eventQueue.remove( currentKey );
        }

        log.info("Executed Background Task at: " + new Timestamp(System.currentTimeMillis()) );
    }


    /**
     * This is currently a dummy Service mimicking the final retrieval of monitoring data
     *
     * @param type  Search for specific workload type
     * @param filter on select workload type
     *
     * @return some event or statistic which can be immediately used
     */
    public String getWorkloadItem(String type, String filter){
        backendConnector.readStatisticEvent( " " );
        return "EMPTY WORKLOAD EVENT";
    }




    /**
     *
     * @param objectType    Specific object type to subscribe to, TABLE,STORE,ADAPTER, etc
     * @param objectId      id of object: unique catalog_id of object
     */
    public void subscribeToEvents( Subscriber subscriber, SubscriptionTopic objectType, long objectId, String description){

        if ( validateSubscription(objectType, objectId) ){
            broker.addSubscription( subscriber, objectType, objectId );
            activeSubscriptionTable.addRow( subscriber.getSubscriptionTitle(), objectType, objectId, description
                    , new Timestamp( System.currentTimeMillis() )
                    ,subscriber.isPersistent() ? "✔" : "X" );

            log.info( "Successfully added Subscription for: "+ subscriber + " to event: "+ objectType + "=" + objectId );
        }
    }

    public void unsubscribeFromEvents( Subscriber subscriber, SubscriptionTopic objectType, long objectId){

        //Only execute if subscriber was even subscribed
        // To save cumbersome traversing of subscription map and save time
        if ( broker.getAllSubscribers().contains( subscriber ) ) {
            broker.removeSubscription( subscriber, objectType, objectId );
        }
    }

    public void unsubscribeFromAllEvents( Subscriber subscriber){
        broker.removeAllSubscriptions( subscriber);
    }



    /**
     *
     * @param objectType    Specific object type to subscribe to, TABLE,STORE,ADAPTER, etc
     * @param objectId      id of object: unique catalog_id of object
     * @return if specified input is correct and usable
     */
    private boolean validateSubscription(SubscriptionTopic objectType, long objectId){

        boolean validation = true;

        //
        //do validation stuff
        //

        if ( !validation ){
            //Todo add custom exception
            throw new RuntimeException("Unable to validate Subscription" );
        }

        return true;
    }


    /*
    * Updates InformationTable with current elements in event queue
     */
    private void updateQueueInformationTable(){

        queueOverviewTable.reset();
        for ( long eventId: eventQueue.keySet() ) {

            MonitorEvent queueEvent = eventQueue.get( eventId );
            queueOverviewTable.addRow( eventId, queueEvent.monitoringType, queueEvent.getDescription(), new Timestamp( queueEvent.getRecordedTimestamp() ),queueEvent.getFieldNames() );
        }
        log.info( "Queue Information Table: REFRESHED" );

    }



    private void initializeMonitoringBackend(){ backendConnector = backendConnectorFactory.getBackendInstance(MONITORING_BACKEND); }

    private class BackendConnectorFactory {

        //Returns backend based on configured statistic Backend in runtimeconfig
        public BackendConnector getBackendInstance( String statisticBackend ) {
            switch ( statisticBackend ) {
                case "InfluxDB":
                    //TODO add error handling or fallback to default backend when no Influx is available
                    return new InfluxBackendConnector();

                case "simple":
                    return new SimpleBackendConnector();

                default :
                    throw new RuntimeException( "Unknown Backend type: '" + statisticBackend  + "' ");
            }


        }

    }



}
