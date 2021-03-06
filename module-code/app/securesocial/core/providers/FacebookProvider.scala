/**
 * Copyright 2012 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package securesocial.core.providers

import play.api.{Application, Logger}
import play.api.libs.json.{JsString, JsObject}
import securesocial.core._
import play.api.libs.ws.{Response, WS}


/**
 * A Facebook Provider
 */
class FacebookProvider(application: Application) extends OAuth2Provider(application) {
  val MeApi = "https://graph.facebook.com/me?fields=name,picture,email&access_token="
  val Error = "error"
  val Message = "message"
  val Type = "type"
  val Id = "id"
  val Name = "name"
  val Picture = "picture"
  val Email = "email"
  val AccessToken = "access_token"
  val Expires = "expires"
  val Data = "data"
  val Url = "url"

  def providerId = FacebookProvider.Facebook

  // facebook does not follow the OAuth2 spec :-\
  override protected def buildInfo(response: Response): OAuth2Info = {
    Logger.debug(providerId + " response body: " + response.body)
    response.body.split("&|=") match {
        case Array(AccessToken, token, Expires, expiresIn) => OAuth2Info(token, None, Some(expiresIn.toInt))
        case _ =>
          Logger.error("Invalid response format for accessToken")
          throw new AuthenticationException()
    }
  }

  def fillProfile(user: SocialUser) = {
    val accessToken = user.oAuth2Info.get.accessToken
    val promise = WS.url(MeApi + accessToken).get()

    promise.await(10000).fold( error => {
      Logger.error( "Error retrieving profile information", error)
      throw new AuthenticationException()
    }, response => {
      val me = response.json
      (me \ Error).asOpt[JsObject] match {
        case Some(error) =>
          val message = (error \ Message).as[String]
          val errorType = ( error \ Type).as[String]
          Logger.error("Error retrieving profile information from Facebook. Error type = " + errorType
            + ", message: " + message)
          throw new AuthenticationException()
        case _ =>
          val id = ( me \ Id).as[String]
          val displayName = ( me \ Name).as[String]
          val picture = (me \ Picture)
          //
          // Starting October 2012 the picture field will become a json object.
          // making the code compatible with the old and new version for now.
          //
          val avatarUrl = if ( picture.isInstanceOf[JsString] ) {
            picture.asOpt[String]
          } else {
            (picture \ Data \ Url).asOpt[String]
          }
          val email = ( me \ Email).as[String]
          user.copy(
            id = UserId(id.toString, providerId),
            displayName = displayName,
            avatarUrl = avatarUrl,
            email = Some(email)
          )
      }
    })
  }
}

object FacebookProvider {
  val Facebook = "facebook"
}