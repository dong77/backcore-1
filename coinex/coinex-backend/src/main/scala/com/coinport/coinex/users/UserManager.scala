/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 *
 * MarketManager is the maintainer of a Market. It executes new orders before
 * they are added into a market as pending orders. As execution results, a list
 * of Transactions are generated and returned.
 *
 * MarketManager can be used by an Akka persistent processor or a view
 * to reflect pending orders and market depth.
 *
 * Note this class does NOT depend on event-sourcing framework we choose. Please
 * keep it plain old scala/java.
 */

package com.coinport.coinex.users

import scala.collection.mutable.{ Map, ListBuffer }
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency.Nxt
import com.coinport.coinex.common.Manager
import com.coinport.coinex.util._
import com.google.common.io.BaseEncoding
import scala.util.Random

class UserManager(googleAuthenticator: GoogleAuthenticator, passwordSecret: String = "") extends Manager[TUserState] {
  // Internal mutable state ----------------------------------------------
  var lastUserId = 1E9.toLong
  val idMap = Map.empty[Long, Long] // email hash to user Id
  val profileMap = Map.empty[Long, UserProfile]
  val referralTokenMap = Map.empty[Long, Long]
  val passwordResetTokenMap: Map[String, Long] = Map.empty[String, Long]
  val verificationTokenMap: Map[String, Long] = Map.empty[String, Long]

  // Thrift conversions     ----------------------------------------------
  def getSnapshot = TUserState(idMap.clone, profileMap.clone, passwordResetTokenMap.clone, verificationTokenMap.clone, lastUserId)

  def loadSnapshot(snapshot: TUserState) = {
    idMap.clear; idMap ++= snapshot.idMap
    profileMap.clear; profileMap ++= snapshot.profileMap
    passwordResetTokenMap.clear; passwordResetTokenMap ++= snapshot.passwordResetTokenMap
    verificationTokenMap.clear; verificationTokenMap ++= snapshot.verificationTokenMap
    lastUserId = snapshot.lastUserId
  }

  // Business logics      ----------------------------------------------
  def regulateProfile(profile: UserProfile, password: String, verificationToken: String, params: Option[ReferralParams]): UserProfile = {
    val email = profile.email.trim.toLowerCase
    val id = getUserId
    val googleAuthenticatorSecret = googleAuthenticator.createSecret

    val referralUserId = for {
      p <- params
      token <- p.referralToken
      referral <- referralTokenMap.get(token)
    } yield referral

    var referralToken = Random.nextLong
    while (referralTokenMap.contains(referralToken)) referralToken = Random.nextLong

    profile.copy(
      id = id,
      email = email,
      emailVerified = false,
      passwordHash = Some(computePassword(id, email, password)),
      passwordResetToken = None,
      verificationToken = Some(verificationToken),
      loginToken = None,
      googleAuthenticatorSecret = None,
      status = UserStatus.Normal,
      referralToken = Some(referralToken),
      referralUserId = referralUserId)
  }

  def registerUser(profile: UserProfile): UserProfile = {
    assert(!profileMap.contains(profile.id))
    addUserProfile(profile)
    verificationTokenMap += profile.verificationToken.get -> profile.id
    lastUserId = profile.id + 1
    profile
  }

  def updateUser(profile: UserProfile): UserProfile = {
    assert(profileMap.contains(profile.id))
    addUserProfile(profile)
    profile
  }

  def requestPasswordReset(email: String, passwordResetToken: String): UserProfile = {
    val profile = getUser(email).get
    passwordResetTokenMap += passwordResetToken -> profile.id
    val updatedProfile = profile.copy(passwordResetToken = Some(passwordResetToken))
    profileMap += profile.id -> updatedProfile
    updatedProfile
  }

  def resetPassword(newPassword: String, passwordResetToken: String): UserProfile = {
    assert(passwordResetTokenMap.contains(passwordResetToken))
    val id = passwordResetTokenMap(passwordResetToken)
    val profile = profileMap(id)
    val passwordHash = computePassword(profile.id, profile.email, newPassword)
    val updatedProfile = profile.copy(passwordHash = Some(passwordHash), passwordResetToken = None)
    profileMap += id -> updatedProfile
    passwordResetTokenMap -= passwordResetToken
    updatedProfile
  }

  def changePassword(email: String, newPassword: String): UserProfile = {
    val profile = getUser(email).get
    val passwordHash = computePassword(profile.id, profile.email, newPassword)
    val passwordResetToken = profile.passwordResetToken.getOrElse("")
    val updatedProfile = profile.copy(passwordHash = Some(passwordHash), passwordResetToken = None)
    //passwordResetTokenMap -= passwordResetToken
    profileMap += profile.id -> updatedProfile
    updatedProfile
  }

  def verifyEmail(verificationToken: String): UserProfile = {
    val id = verificationTokenMap(verificationToken)
    val profile = profileMap(id).copy(emailVerified = true, verificationToken = None)
    profileMap += id -> profile
    verificationTokenMap -= verificationToken
    profile
  }

  def cleanData(actions: scala.collection.Set[CleanActionType]): List[UserProfile] = {
    actions.toList.map {
      ac =>
        ac match {
          case CleanActionType.NxtAddressIncomplete =>
            cleanNxtDepositAddress()
          case CleanActionType.RemoveNxtAddressNoPubkey =>
            cleanNxtDepositAddress(true)
          case _ =>
            List.empty[UserProfile]
        }
    }.reduce(_ ++ _)
  }

  def checkLogin(email: String, password: String): Either[ErrorCode, UserProfile] =
    getUser(email) match {
      case None => Left(ErrorCode.UserNotExist)
      case Some(profile) =>
        val passwordHash = computePassword(profile.id, profile.email, password)
        if (!profile.emailVerified) Left(ErrorCode.EmailNotVerified)
        else if (Some(passwordHash) == profile.passwordHash) Right(profile)
        else Left(ErrorCode.PasswordNotMatch)
    }

  def getUser(email: String): Option[UserProfile] = {
    idMap.get(MHash.murmur3(email.trim.toLowerCase)).map(profileMap.get(_).get)
  }

  def getUserWithPasswordResetToken(token: String): Option[UserProfile] = {
    passwordResetTokenMap.get(token).map(profileMap.get(_).get)
  }

  def getUserWithVerificationToken(token: String): Option[UserProfile] = {
    verificationTokenMap.get(token).map(profileMap.get(_).get)
  }

  private def addUserProfile(profile: UserProfile) = {
    idMap += MHash.murmur3(profile.email) -> profile.id
    profileMap += profile.id -> profile
    profile.referralToken foreach { referralTokenMap += _ -> profile.id }
  }

  private def getUserId = lastUserId

  private def computePassword(id: Long, email: String, password: String) =
    MHash.sha256Base64(email + passwordSecret + MHash.sha256Base64(id + password.trim + passwordSecret))

  private def cleanNxtDepositAddress(isRemoveNoPubKey: Boolean = false): List[UserProfile] = {
    val resultProfiles = ListBuffer.empty[UserProfile]
    profileMap.keys.foreach {
      userId =>
        val pf = profileMap(userId)
        pf.depositAddresses.foreach {
          addresses =>
            if (addresses.contains(Nxt)) {
              val nxtAd = addresses.get(Nxt).getOrElse("")
              if (isRemoveNoPubKey) {
                if (nxtAd.length < 50 || nxtAd.split("//").size < 3) {
                  val newProfile = pf.copy(depositAddresses = Some(addresses - Nxt))
                  profileMap.update(userId, newProfile)
                  resultProfiles += newProfile
                }
              } else {
                if (nxtAd.length < 35 || nxtAd.startsWith("//NXT")) {
                  val newProfile = pf.copy(depositAddresses = Some(addresses - Nxt))
                  profileMap.update(userId, newProfile)
                  resultProfiles += newProfile
                }
              }
            }
        }
    }
    resultProfiles.toList
  }

  def getNxtDepositAddress(): List[String] = {
    profileMap.values.filter {
      i => i.depositAddresses.isDefined && i.depositAddresses.get.contains(Nxt) && i.depositAddresses.get.get(Nxt).get.nonEmpty
    }.map {
      _.depositAddresses.get.get(Nxt).get
    }.toList
  }

  def updateNxtDepositAddresses(cryptoAdds: Seq[CryptoAddress]) {
    val cryptoAddMap = cryptoAdds.map {
      i => i.address -> i
    }.toMap
    profileMap.keys.filter {
      i => profileMap(i).depositAddresses.isDefined && profileMap(i).depositAddresses.get.contains(Nxt)
    }.foreach {
      userId =>
        val formerAdd = profileMap(userId).depositAddresses.get.get(Nxt).get.split("//").head
        if (formerAdd.nonEmpty && cryptoAddMap.contains(formerAdd)) {
          val ca = cryptoAddMap(formerAdd)
          val newNxtAdd = s"${ca.address}//${ca.nxtRsAddress}//${ca.nxtPublicKey}"
          val newDepositAddress = Map.empty[Currency, String] += (Nxt -> newNxtAdd)
          profileMap.update(userId, profileMap(userId).copy(depositAddresses = Some(newDepositAddress ++ profileMap(userId).depositAddresses.get)))
        }
    }
  }
}
