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
import com.mongodb.MongoURI;
import com.mongodb.WriteConcern;

public class MongoTest {
	
	private static ExecutorService executor = Executors.newFixedThreadPool(50);

	private static volatile long time;
	public static void main(String[] args) {
		try {
			Mongo mongo = new Mongo(new MongoURI("mongodb://127.0.0.1:29017"));
			final DB db = mongo.getDB("auditlog_c88932a2-06ae-40a7-9d64-6bc6847f18df");
			db.setWriteConcern(WriteConcern.FSYNC_SAFE);
			final DBCollection test = db.getCollection("testing");

			final int count = 1;
			final CountDownLatch latch = new CountDownLatch(count);
			for (int i = 0; i < count; i++) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						long start = System.currentTimeMillis();
						db.requestStart();
						try {
							BasicDBObject o = new BasicDBObject ("test" + UUID.randomUUID(), UUID.randomUUID());
							test.insert(o);
						} finally {
							db.requestDone();
						}
						time += System.currentTimeMillis() - start;
						latch.countDown();
					}
				});
			}
			
			latch.await();
			
			System.out.println("Time taken: " + time + ", avg: " + (time / count));
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
}
