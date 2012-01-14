package com.tradeshift.mongoproxy;

import java.net.UnknownHostException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.MongoOptions;
import com.mongodb.MongoURI;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;

public class MongoTest {
	
	private static ExecutorService executor = Executors.newFixedThreadPool(100);

	private static volatile long time;
	public static void main(String[] args) {
		try {
			MongoOptions opts = new MongoOptions();
			opts.connectionsPerHost = 100;
			opts.autoConnectRetry = true;
			opts.connectTimeout = 2000;
			opts.socketTimeout = 10000;
					
			Mongo mongo = new Mongo(new ServerAddress("127.0.0.1", 29017), opts);
			final DB db = mongo.getDB("auditlog_c88932a2-06ae-40a7-9d64-6bc6847f18df");
			db.setWriteConcern(WriteConcern.FSYNC_SAFE);
			final DBCollection test = db.getCollection("testing");

			System.out.println("Warming up");
			for (int i = 0; i < 10; i++) {
				run(db, test);
			}
			System.out.println("Running test");
			
			final int count = 10000;
			final CountDownLatch latch = new CountDownLatch(count);
			for (int i = 0; i < count; i++) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						try {
							for (int i = 0; i < 100; i++) {
								long start = System.currentTimeMillis();
								MongoTest.run(db, test);
								time += System.currentTimeMillis() - start;
							}
						} finally {
							latch.countDown();
						}
					}

				});
			}
			
			latch.await();
			
			System.out.println("Time taken: " + time + ", avg: " + (time / (count * 100)));
			System.exit(0);
		} catch (MongoException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void run(final DB db, final DBCollection test) {
		db.requestStart();
		try {
			BasicDBObject o = new BasicDBObject ("test" + UUID.randomUUID(), UUID.randomUUID());
			test.insert(o);
		} finally {
			db.requestDone();
		}
	}

}
