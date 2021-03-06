/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.shadowmask.jdbc.connection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.log4j.Logger;
import org.shadowmask.cache.PoolByKey;
import org.shadowmask.jdbc.connection.description.KerberizedHive2JdbcConnDesc;

public class
KerberizedHiveConnectionProvider<DESC extends KerberizedHive2JdbcConnDesc>
    extends ConnectionProvider<DESC> {

  private static Logger logger =
      Logger.getLogger(KerberizedHiveConnectionProvider.class);


  private PoolByKey<String, Connection> cache = new PoolByKey<String, Connection>() {

    @Override
    protected Connection getObjectFromKey(String k) {
      try {
        return DriverManager.getConnection(k);
      } catch (SQLException e) {
        logger.warn("get jdbc connection failed", e);
        throw new RuntimeException("get connection failed", e);
      }
    }

    @Override
    protected void touch(Connection connection) {
      PreparedStatement stm = null;
      try {
        stm = connection.prepareStatement("SELECT  1");
        stm.executeQuery();
      } catch (Exception e) {
        logger.info(e.getMessage(), e);
      } finally {
        if (stm != null) {
          try {
            stm.close();
          } catch (Exception e) {
            logger.info(e.getMessage(), e);
          }
        }
      }
    }
  };

  Map<Connection, String> connectionUrl = new ConcurrentHashMap<>();


  @Override
  public Connection get(DESC desc) {
    Connection connection = cache.borrow(desc.toUrl());
    connectionUrl.put(connection, desc.toUrl());
    return connection;
  }

  @Override
  public void release(Connection connection) {
    cache.release(connectionUrl.get(connection), connection);
  }

  // singleton
  private KerberizedHiveConnectionProvider() {
  }

  private static KerberizedHiveConnectionProvider instance =
      new KerberizedHiveConnectionProvider<KerberizedHive2JdbcConnDesc>();

  public static KerberizedHiveConnectionProvider getInstance() {
    return instance;
  }

}
