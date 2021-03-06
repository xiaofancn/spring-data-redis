/*
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.redis.connection.srp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.data.redis.RedisVersionUtils;
import org.springframework.data.redis.connection.AbstractConnectionPipelineIntegrationTests;
import org.springframework.data.redis.connection.DefaultStringTuple;
import org.springframework.data.redis.connection.RedisZSetCommands.Tuple;
import org.springframework.data.redis.connection.StringRedisConnection.StringTuple;
import org.springframework.data.redis.serializer.SerializationUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import redis.reply.BulkReply;
import redis.reply.Reply;

/**
 * Integration test of {@link SrpConnection} pipeline functionality
 *
 * @author Jennifer Hickey
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("SrpConnectionIntegrationTests-context.xml")
public class SrpConnectionPipelineIntegrationTests extends
		AbstractConnectionPipelineIntegrationTests {

	@Ignore("DATAREDIS-169 SRP discard does not clear txReplies, results in inconsistent results on next tx exec")
	public void testMultiDiscard() {
	}

	@Ignore("DATAREDIS-168 SRP exec throws TransactionFailedException if watched value modified")
	public void testWatch() {
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testZInterStoreAggWeights() {
		super.testZInterStoreAggWeights();
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testZUnionStoreAggWeights() {
		super.testZUnionStoreAggWeights();
	}

	// Overrides, usually due to return values being Long vs Boolean or Set vs
	// List

	@Test
	public void testInfo() throws Exception {
		assertNull(connection.info());
		List<Object> results = getResults();
		assertEquals(1, results.size());
		Properties info = SrpUtils.info(new BulkReply((byte[]) results.get(0)));
		assertTrue("at least 5 settings should be present", info.size() >= 5);
		String version = info.getProperty("redis_version");
		assertNotNull(version);
	}

	@Test
	public void testExists() {
		connection.set("existent", "true");
		actual.add(connection.exists("existent"));
		actual.add(connection.exists("nonexistent"));
		verifyResults(Arrays.asList(new Object[] { 1l, 0l }), actual);
	}

	@Test
	public void testRename() {
		connection.set("renametest", "testit");
		connection.rename("renametest", "newrenametest");
		actual.add(connection.get("newrenametest"));
		actual.add(connection.exists("renametest"));
		verifyResults(Arrays.asList(new Object[] { "testit", 0l }), actual);
	}

	@Test
	public void testSIsMember() {
		convertResultToSet = true;
		actual.add(connection.sAdd("myset", "foo"));
		actual.add(connection.sAdd("myset", "bar"));
		actual.add(connection.sIsMember("myset", "foo"));
		actual.add(connection.sIsMember("myset", "baz"));
		verifyResults(Arrays.asList(new Object[] { 1l, 1l, 1l, 0l }), actual);
	}

	@Test
	public void testZIncrBy() {
		actual.add(connection.zAdd("myset", 2, "Bob"));
		actual.add(connection.zAdd("myset", 1, "James"));
		actual.add(connection.zAdd("myset", 4, "Joe"));
		actual.add(connection.zIncrBy("myset", 2, "Joe"));
		actual.add(connection.zRangeByScore("myset", 6, 6));
		verifyResults(
				Arrays.asList(new Object[] { 1l, 1l, 1l, "6", Collections.singletonList("Joe") }),
				actual);
	}

	@Test
	public void testZScore() {
		actual.add(connection.zAdd("myset", 2, "Bob"));
		actual.add(connection.zAdd("myset", 1, "James"));
		actual.add(connection.zAdd("myset", 3, "Joe"));
		actual.add(connection.zScore("myset", "Joe"));
		verifyResults(Arrays.asList(new Object[] { 1l, 1l, 1l, "3" }), actual);
	}

	@Test
	public void testGetRangeSetRange() {
		connection.closePipeline();
		boolean getRangeSupported = RedisVersionUtils.atLeast("2.4.0", connection);
		connection.openPipeline();
		assumeTrue(getRangeSupported);
		super.testGetRangeSetRange();
	}

	protected Object convertResult(Object result) {
		Object convertedResult = super.convertResult(result);
		if (convertedResult instanceof Reply[]) {
			if (convertResultToSet) {
				return SerializationUtils.deserialize(SrpUtils.toSet((Reply[]) convertedResult),
						stringSerializer);
			} else if (convertResultToTuples) {
				Set<Tuple> tuples = SrpUtils.convertTuple((Reply[]) convertedResult);
				List<StringTuple> stringTuples = new ArrayList<StringTuple>();
				for (Tuple tuple : tuples) {
					stringTuples.add(new DefaultStringTuple(tuple, new String(tuple.getValue())));
				}
				return stringTuples;
			} else {
				return SerializationUtils.deserialize(
						SrpUtils.toBytesList((Reply[]) convertedResult), stringSerializer);
			}
		}
		return convertedResult;
	}
}
