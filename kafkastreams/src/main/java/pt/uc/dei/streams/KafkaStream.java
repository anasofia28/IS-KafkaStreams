package pt.uc.dei.streams;

import java.io.IOException;
import java.time.Duration;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serdes.IntegerSerde;
import org.apache.kafka.common.serialization.Serdes.StringSerde;


import java.util.Properties;


import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;



public class KafkaStream {

    // static <T extends SpecificRecord> SpecificAvroSerde<T> getSpecificAvroSerde(Map<String, Object> serdeConfig) {
    //     SpecificAvroSerde<T> specificAvroSerde = new SpecificAvroSerde<>();
    //     specificAvroSerde.configure(serdeConfig, false);
    //     return specificAvroSerde;
    // }

    public static void main(String[] args) throws InterruptedException, IOException {
        

        final String inputTopic = "t1-f";
        final String inputTopic2 = "t2-f";
        final String outputTopic = "result-topic1";

        Duration windowSize = Duration.ofMinutes(60);
        Duration advanceSize = Duration.ofMinutes(60);
        TimeWindows hoppingWindow = TimeWindows.ofSizeWithNoGrace(windowSize).advanceBy(advanceSize);
        //long joinWindowSizeMs = 60L * 60L * 1000L;
        
        Properties streamProps = new Properties();

        streamProps.put(StreamsConfig.APPLICATION_ID_CONFIG, "id111");
        streamProps.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "broker1:9092");
        streamProps.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        streamProps.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();
        
        KStream<String, String> mainStreamStandard = builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> mainStreamAlert = builder.stream(inputTopic2, Consumed.with(Serdes.String(), Serdes.String()));
        
        /* Ver exemplos no Theoretical !!*/

        // Count temperature readings of standard weather events per weather station.
        mainStreamStandard.groupByKey()
                          .count()
                          .toStream()
                          .filter((key, value) -> value != null)
                          .peek((key, value) -> System.out.println("-1- Outgoind record - key " + key + " value " + value))
                          .to(outputTopic, Produced.with(Serdes.String(), Serdes.Long()));
                          
        // Count	temperature	readings	of	standard	weather	events	per	location 
        mainStreamStandard.map((k, v) -> new KeyValue<>(v.split(":")[0], v.split(":")[1]))
                        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .count()
                        .toStream()
                        .filter((key, value) -> value != null)
                        .peek((key, value) -> System.out.println("-2- Outgoind record - key " + key + " value " + value))
                        .to(outputTopic, Produced.with(Serdes.String(), Serdes.Long()));


        // Get	minimum	and	maximum	temperature	per	weather	station. 

        KTable<String, int[]> minMaxTemp = mainStreamStandard.groupByKey()
                          .aggregate( () -> new int[]{-200, -200}, (aggKey, newVal, aggValue) -> {
                            if(aggValue[0] == -200){
                                aggValue[0] = Integer.valueOf(newVal.split(":")[1]);
                                aggValue[1] = Integer.valueOf(newVal.split(":")[1]);
                            }
                            else{
                                aggValue[0] = Math.min(aggValue[0], Integer.valueOf(newVal.split(":")[1]));
                                aggValue[1] = Math.max(aggValue[1], Integer.valueOf(newVal.split(":")[1]));
                            }
                                
                            return aggValue;
                          }, Materialized.with(Serdes.String(), new IntArraySerde()));
                        
                    

        minMaxTemp.mapValues(v -> "Min " + v[0] + "/Max " + v[1])
                .toStream()
                .filter((key, value) -> value != null)
                .peek((key, value) -> System.out.println("-3-Outgoind record - key " + key + " value " + value))
                .to(outputTopic + "-3", Produced.with(Serdes.String(), Serdes.String()));


        // Get	minimum	and	maximum	temperature	per	location	(Students	should	computethese	values	in	Fahrenheit). 

        mainStreamStandard.map((k, v) -> new KeyValue<>(v.split(":")[0], v.split(":")[1]))


                        .groupByKey()
                        .aggregate( () -> new int[]{-200, -200}, (aggKey, newVal, aggValue) -> {
                        if(aggValue[0] == -200){
                            aggValue[0] = Integer.valueOf(newVal);
                            aggValue[1] = Integer.valueOf(newVal);
                        }
                        else{
                            aggValue[0] = Math.min(aggValue[0], Integer.valueOf(newVal));
                            aggValue[1] = Math.max(aggValue[1], Integer.valueOf(newVal));
                        }
                            
                        return aggValue;
                        }, Materialized.with(Serdes.String(), new IntArraySerde()))

                    .mapValues(v -> "Min " + (v[0] * 1.9 + 32) + " F / Max " + (v[1] * 1.9 + 32) + " F")
                    .toStream()
                    .filter((key, value) -> value != null)
                    .peek((key, value) -> System.out.println("-4-Outgoing record - key " + key + " value " + value))
                    .to(outputTopic + "-4", Produced.with(Serdes.String(), Serdes.String()));


        // Count	the	total	number	of	alerts	per	weather	station 

        mainStreamAlert.groupByKey()
                          .count()
                          .toStream()
                          .filter((key, value) -> value != null)
                          .peek((key, value) -> System.out.println("-5- Outgoind record - key " + key + " value " + value))
                          .to(outputTopic, Produced.with(Serdes.String(), Serdes.Long()));
                          

        //  Count	the	total	alerts	per	type. 

        mainStreamAlert.map((k, v) -> new KeyValue<>(k, v.split(":")[1]))
                        .groupByKey()
                        .aggregate( () -> new int[]{0, 0}, (aggKey, newVal, aggValue) -> {
                            if(newVal.equals("clear")){
                                aggValue[0] += 1;
                            }
                            else{
                                aggValue[1] += 1;
                            }
                            return aggValue;
                        }, Materialized.with(Serdes.String(), new IntArraySerde()))             

                    .mapValues(v -> "Green: " + v[0] + " / Red " + v[1])
                    .toStream()
                    .filter((key, value) -> value != null)
                    .peek((key, value) -> System.out.println("-6-Outgoing record - key " + key + " value " + value))
                    .to(outputTopic + "-6", Produced.with(Serdes.String(), Serdes.String()));

        // Get	minimum	temperature of	weather	stations	with	red	alert	events. 
        KStream<String, String> joined = mainStreamAlert.join(minMaxTemp,
            (leftValue, rightValue) -> "left/" + leftValue + "/right/" + rightValue[0] /* ValueJoiner */);


        joined.groupByKey()
            .aggregate( () -> -200,(aggKey, newVal, aggValue) -> {
                if(aggValue == -200){
                    aggValue = Integer.valueOf(newVal.split("/")[3]);
                }

                return aggValue;
            }, Materialized.with(Serdes.String(), new IntegerSerde()))
            .toStream()
            .filter((key, value) -> value != null)
            .peek((key,value)-> System.out.println("-7- key " + key + " value " + value))
            .to(outputTopic, Produced.with(Serdes.String(), Serdes.Integer()));
            

        
        //Get	 maximum	 temperature	 of	 each	 location	 of	 alert	 events	 for the	 last	 hour	(students	are	allowed	to	define a	different	value	for	the	time	window). 
        
        KStream<String, String> joinedMax = mainStreamAlert.join(minMaxTemp,
            (leftValue, rightValue) -> "left/" + leftValue + "/right/" + rightValue[1] /* ValueJoiner */
        );

        joinedMax.groupByKey()
        .windowedBy(hoppingWindow)
        .aggregate( () -> -200,(aggKey, newVal, aggValue) -> {
            if(aggValue == -200){
                aggValue = Integer.valueOf(newVal.split("/")[3]);
            }

            return aggValue;
        }, Materialized.with(Serdes.String(), new IntegerSerde()))
        .toStream()
        .map((wk, value) -> KeyValue.pair(wk.key(),value))
        .filter((key, value) -> value != null)
        .peek((key, value) -> System.out.println("-8-Outgoing record - key " + key + " value " + value))
        .to(outputTopic + "-8", Produced.with(Serdes.String(), Serdes.Integer()));

        // Get	minimum	temperature	per	weather	station	in	red	alert	zones. 

        // Transform Standard stream to KTable with the lowest temperature

        KTable<String, String> table1 = mainStreamStandard.map((k, v) -> new KeyValue<>(k, v.split(":")[1]))
                        .groupByKey()
                        .aggregate( () -> "", (aggKey, newVal, aggValue) -> {
                            return newVal;
                        }, Materialized.with(Serdes.String(), new StringSerde()));

                    
        // Transform Alert stream to KTable
        KTable<String, String> table2 = mainStreamAlert.map((k, v) -> new KeyValue<>(k, v.split(":")[1]))
                        .groupByKey()
                        .aggregate( () -> "", (aggKey, newVal, aggValue) -> {
                            return newVal;
                        }, Materialized.with(Serdes.String(), new StringSerde()));

        // Inner Join of KTable, Ktable
        // The most recent temperature read is associated with the most recent alert read
        KTable<String, String> joinedT = table1.join(table2,
            (leftValue, rightValue) -> "(" + leftValue + "," + rightValue + ")");

        joinedT.toStream()
            .filter((key, value) -> value != null)
            .peek((key, value) -> System.out.println("-9- Outgoing record - key " + key + " value " + value))
            .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));

        // Get	the	average	temperature	per	weather	station.
        
        mainStreamStandard
            .groupByKey()
            .aggregate(() -> new int[]{0, 0}, (aggKey, newValue, aggValue) -> {
                aggValue[0] += 1;
                aggValue[1] += Integer.valueOf(newValue.split(":")[1]);

                return aggValue;
            }, Materialized.with(Serdes.String(), new IntArraySerde()))
            .mapValues(v -> v[0] != 0 ? "" + (1.0 * v[1]) / v[0] : "div by 0")
            .toStream()
            .filter((key, value) -> value != null)
            .peek((key, value) -> System.out.println("-10-Outgoind record - key " + key + " value " + value))
            .to(outputTopic + "-", Produced.with(Serdes.String(), Serdes.String()));
        

        // Get	the	average	temperature	of	weather	stations	with	red	alert	events	for the	last	hour

        // Aggregate to get [sum, count] in the last time window
        KTable<String, int[]> averageTemp = mainStreamStandard
            .groupByKey()
            .aggregate( () -> new int[]{0 ,0}, (aggKey, newVal, aggValue) -> {
            aggValue[0] += Integer.valueOf(newVal.split(":")[1]);
            aggValue[1] += 1;  
            return aggValue;
            }, Materialized.with(Serdes.String(), new IntArraySerde()));
        

        // Join weather stations with their [sum,count] and their respective red alert events
        KStream<String, String> joined2 = mainStreamAlert.join(averageTemp,
        (leftValue, rightValue) -> "left/" + leftValue + "/right/" + (float)rightValue[0]/rightValue[1]); // left value = key, right value = [sum, count] -> [0]/[1] = average);

        // Key = Red alert event, Value = Average temperature
        KStream<String, String> avgStream = joined2.map((k, v) -> new KeyValue<>(k, v.split("/")[3])); // value = average
         
        avgStream
            .groupByKey()
            .windowedBy(hoppingWindow)
            .aggregate( () -> "", (aggKey, newVal, aggValue) -> {
                return newVal;
            }, Materialized.with(Serdes.String(), new StringSerde()))   
            .toStream()
            .mapValues(v -> "Average: " + v)
            .map((wk, value) -> KeyValue.pair(wk.key(),value))
            .filter((key, value) -> value != null)
            .peek((key, value) -> System.out.println("-11-Outgoing record - key " + key + " value " + value))
            .to(outputTopic + "-11", Produced.with(Serdes.String(), Serdes.String()));

        KafkaStreams kafkaStreams = new KafkaStreams(builder.build(), streamProps);
        kafkaStreams.start();
    }   

}
