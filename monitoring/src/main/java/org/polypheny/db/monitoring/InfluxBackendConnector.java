package org.polypheny.db.monitoring;


import com.influxdb.LogLevel;
import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.HealthCheck;
import com.influxdb.client.domain.HealthCheck.StatusEnum;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.FluxTable;
import com.influxdb.query.internal.FluxResultMapper;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;

//ToDO Cedric just moved this the conenctor backend without much refactoring
// please check if this is still working
public class InfluxBackendConnector implements BackendConnector{

    static InfluxDBClient client;

    // InfluxDB needs to be started to use monitoring in a proper way.
    // I tested the implementation with the docker image, working just fine and explained here:
    // https://docs.influxdata.com/influxdb/v2.0/get-started/?t=Docker#

    // You can generate a Token from the "Tokens Tab" in the UI
    // TODO: Add your own token and config here!

    static String token = "EvyOwXhnCxKwAd25pUq41o3n3O3um39qi8bRtr134adzzUu_vCyxFJ8mKLqHeQ0MRpt6uEiH3dkkhL6gkctzpw==";
    static String bucket = "polypheny-monitoring";
    static String org = "unibas";
    static String url = "http://localhost:8086";


    // For influxDB testing purpose
    public static void main(final String[] args) {

        InfluxDBClient client = InfluxDBClientFactory.create(url, token.toCharArray());

        InfluxPojo data = InfluxPojo.Create( "sql statement", "sql statement type", new Random().nextLong());
        try ( WriteApi writeApi = client.getWriteApi()) {
            writeApi.writeMeasurement(bucket, org, WritePrecision.NS, data);
        }

        // Import to query with the pivot command:
        // from(bucket: "polypheny-monitoring")
        //    |> range(start: -1h)
        //    |> filter(fn: (r) => r["_measurement"] == "Query")
        //    |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")

        // IMPORTANT: range always need to be defined!

        String query = String.format("from(bucket: \"%s\") |> range(start: -1h) |> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") |> filter(fn: (r) => r[\"_measurement\"] == \"Query\")", bucket);

        List<InfluxPojo> results = client.getQueryApi().query( query, org, InfluxPojo.class);

        results.forEach( (InfluxPojo elem) -> System.out.println(elem.toString()) );

        client.close();
    }

    @Override
    public void initializeConnectorClient(){
        if(client == null) {
            client = InfluxDBClientFactory.create("http://localhost:8086", token.toCharArray());
        }
    }


    @Override
    public void monitorEvent() {
        monitorEvent(new InfluxPojo());
    }


    @Override
    public void writeStatisticEvent( String incomingEvent ) {
        throw new RuntimeException("InfluxBackendConnector: Not implemented yet");
    }


    @Override
    public void readStatisticEvent( String outgoingEvent ) {
        throw new RuntimeException("InfluxBackendConnector: Not implemented yet");
    }


    //TODO this is currently rather specific to InfluxDB move this too a backend connector
    //Monitoring Service should be the "interface" commonly used in code.
    public void monitorEvent(InfluxPojo data){
        // check if client is initialized
        if( client == null){
            initializeConnectorClient();
        }

        // check if client is available
        if (client != null) {
            HealthCheck healthCheck = client.health();
            if(healthCheck.getStatus() == StatusEnum.PASS) {
                try ( WriteApi writeApi = client.getWriteApi()) {
                    writeApi.writeMeasurement(bucket, org, WritePrecision.NS, data);
                    writeApi.flush();
                }
            }
        }
    }
}
