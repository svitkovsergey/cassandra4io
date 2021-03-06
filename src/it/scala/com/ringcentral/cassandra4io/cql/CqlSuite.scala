package com.ringcentral.cassandra4io.cql

import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.ringcentral.cassandra4io.CassandraTestsSharedInstances
import fs2.Stream
import weaver._

import java.time.Duration

trait CqlSuite { self: IOSuite with CassandraTestsSharedInstances =>

  case class Data(id: Long, data: String)

  test("interpolated select template should return data from migration") { session =>
    for {
      prepared <- cqlt"select data FROM cassandra4io.test_data WHERE id in ${Put[List[Long]]}"
                    .as[String]
                    .config(_.setTimeout(Duration.ofSeconds(1)))
                    .prepare(session)
      query     = prepared(List[Long](1, 2, 3))
      results  <- query.select.compile.toList
    } yield expect(results == Seq("one", "two", "three"))
  }

  test("interpolated select template should return tuples from migration") { session =>
    for {
      prepared <- cqlt"select id, data FROM cassandra4io.test_data WHERE id in ${Put[List[Long]]}"
                    .as[(Long, String)]
                    .prepare(session)
      query     = prepared(List[Long](1, 2, 3))
      results  <- query.select.compile.toList
    } yield expect(results == Seq((1, "one"), (2, "two"), (3, "three")))
  }

  test("interpolated select template should return tuples from migration with multiple binding") { session =>
    for {
      query   <-
        cqlt"select data FROM cassandra4io.test_data_multiple_keys WHERE id1 = ${Put[Long]} and id2 = ${Put[Int]}"
          .as[String]
          .prepare(session)
      results <- query(1L, 2).config(_.setExecutionProfileName("default")).select.compile.toList
    } yield expect(results == Seq("one-two"))
  }

  test("interpolated select template should return tuples from migration with multiple binding and margin stripped") {
    session =>
      for {
        query   <- cqlt"""select data FROM cassandra4io.test_data_multiple_keys
                       |WHERE id1 = ${Put[Long]} and id2 = ${Put[Int]}""".stripMargin.as[String].prepare(session)
        results <- query(1L, 2).config(_.setExecutionProfileName("default")).select.compile.toList
      } yield expect(results == Seq("one-two"))
  }

  test("interpolated select template should return data case class from migration") { session =>
    for {
      prepared <-
        cqlt"select id, data FROM cassandra4io.test_data WHERE id in ${Put[List[Long]]}".as[Data].prepare(session)
      query     = prepared(List[Long](1, 2, 3))
      results  <- query.select.compile.toList
    } yield expect(results == Seq(Data(1, "one"), Data(2, "two"), Data(3, "three")))
  }

  test("interpolated select template should be reusable") { session =>
    for {
      query  <- cqlt"select data FROM cassandra4io.test_data WHERE id = ${Put[Long]}".as[String].prepare(session)
      result <- Stream.emits(Seq(1L, 2L, 3L)).flatMap(i => query(i).select).compile.toList
    } yield expect(result == Seq("one", "two", "three"))
  }

  test("interpolated select should return data from migration") { session =>
    def getDataByIds(ids: List[Long]) =
      cql"select data FROM cassandra4io.test_data WHERE id in $ids"
        .as[String]
        .config(_.setConsistencyLevel(ConsistencyLevel.ALL))
    for {
      results <- getDataByIds(List(1, 2, 3)).select(session).compile.toList
    } yield expect(results == Seq("one", "two", "three"))
  }

  test("interpolated select should return tuples from migration") { session =>
    def getAllByIds(ids: List[Long]) =
      cql"select id, data FROM cassandra4io.test_data WHERE id in $ids".as[(Long, String)]
    for {
      results <- getAllByIds(List(1, 2, 3)).config(_.setQueryTimestamp(0L)).select(session).compile.toList
    } yield expect(results == Seq((1, "one"), (2, "two"), (3, "three")))
  }

  test("interpolated select should return tuples from migration with multiple binding") { session =>
    def getAllByIds(id1: Long, id2: Int) =
      cql"select data FROM cassandra4io.test_data_multiple_keys WHERE id1 = $id1 and id2 = $id2".as[String]
    for {
      results <- getAllByIds(1, 2).select(session).compile.toList
    } yield expect(results == Seq("one-two"))
  }

  test("interpolated select should return tuples from migration with multiple binding and margin stripped") { session =>
    def getAllByIds(id1: Long, id2: Int) =
      cql"""select data FROM cassandra4io.test_data_multiple_keys
           |WHERE id1 = $id1 and id2 = $id2""".stripMargin.as[String]
    for {
      results <- getAllByIds(1, 2).select(session).compile.toList
    } yield expect(results == Seq("one-two"))
  }

  test("interpolated select should return data case class from migration") { session =>
    def getIds(ids: List[Long]) =
      cql"select id, data FROM cassandra4io.test_data WHERE id in $ids".as[Data]
    for {
      results <- getIds(List(1, 2, 3)).select(session).compile.toList
    } yield expect(results == Seq(Data(1, "one"), Data(2, "two"), Data(3, "three")))
  }

  test("interpolated select should bind constants") { session =>
    val query = cql"select data FROM cassandra4io.test_data WHERE id = ${1L}".as[String]
    for {
      result <- query.select(session).compile.toList
    } yield expect(result == Seq("one"))
  }

}
