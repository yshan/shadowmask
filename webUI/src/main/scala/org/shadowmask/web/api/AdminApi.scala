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

package org.shadowmask.web.api

import org.json4s._
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet.FileUploadSupport
import org.scalatra.swagger._
import org.shadowmask.web.common.user.{ConfiguredAuthProvider, Token, User}
import org.shadowmask.web.model._
import org.shadowmask.web.service.HiveService

class AdminApi(implicit val swagger: Swagger) extends ScalatraServlet
  with FileUploadSupport
  with JacksonJsonSupport
  with SwaggerSupport
  with ConfiguredAuthProvider {

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected val applicationDescription: String = "AdminApi"
  override protected val applicationName: Option[String] = Some("admin")

  before() {
    contentType = formats("json")
    response.headers += ("Access-Control-Allow-Origin" -> "*")
  }

  error {
    case e: Exception =>
      e.printStackTrace()
  }


  val adminUsersGetOperation = (apiOperation[UserResult]("adminUsersGet")
    summary "get all users."
    parameters(headerParam[String]("authorization").description(""),
    queryParam[String]("dcName").description(""))
    )

  get("/users", operation(adminUsersGetOperation)) {
    val authToken = request.getHeader("Authorization")
    val u = getAuth().verify(Some(Token(authToken)))
    if (u == None) {
      halt(200, SimpleResult(Some(1), Some("authorization failed")), Map(), "");
    }
    val dcName = params.getAs[String]("dcName")
    UserResult(
      Some(0),
      Some("ok"),
      Some(
        UserItemObj(
          Some(HiveService().getAllRoles(dcName.get).map(name => UserItem(Some(name), Some(name))).toList)
        )
      )
    )
  }

  val adminLoginPostOperation = (apiOperation[LoginResult]("adminLoginPost")
    summary "Administrator login api"
    parameters(formParam[String]("username").description("administrator'name")
    , formParam[String]("password").description("administrator'password"))
    )

  post("/login", operation(adminLoginPostOperation)) {
    val username = params.getAs[String]("username")
    val password = params.getAs[String]("password")
    val (code, info, loginData) =
      getAuth().auth(Some(User(username.getOrElse(""), password.getOrElse("")))) match {
        case Some(token) => (Some(0), Some("successfully"), Some(LoginResultData(Some(token.token), username)))
        case _ => (Some(1), Some("failed"), Some(LoginResultData(Some(""), Some(""))))
      }
    LoginResult(code, info, loginData)
  }


  val adminGrantPostOperation = (apiOperation[SimpleResult]("adminGrantPost")
    summary "grant priviledges."
    parameters(
    headerParam[String]("Authorization").description("authentication token"),
    formParam[String]("source").description("database type, HIVE,SPARK, etc"),
    formParam[String]("datasetType").description("data set type ,TABLE,VIEW, etc"),
    formParam[String]("schema").description("the schema which the datasetType belongs to."),
    formParam[String]("name").description("table/view name"),
    formParam[String]("user").description("someone who the table will be granted to ."))
    )

  post("/grant", operation(adminGrantPostOperation)) {
    val authToken = request.getHeader("Authorization")
    val u = getAuth().verify(Some(Token(authToken)))
    if (u == None) {
      halt(200, SimpleResult(Some(1), Some("authorization failed")), Map(), "");
    }

    val source = params.getAs[String]("source")
    val datasetType = params.getAs[String]("datasetType")
    val schema = params.getAs[String]("schema")
    val name = params.getAs[String]("name")
    val user = params.getAs[String]("user")
    HiveService().grant(source.get, schema.get, name.get, user.get)

    SimpleResult(Some(0), Some("ok"));
  }


}
