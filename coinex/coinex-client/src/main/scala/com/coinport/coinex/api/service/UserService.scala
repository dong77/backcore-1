/**
 * Copyright (C) 2014 Coinport Inc.
 */

package com.coinport.coinex.api.service

import com.coinport.coinex.api.model._
import com.coinport.coinex.data._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await.result
import scala.concurrent.Await

object UserService extends AkkaService {
  override def hashCode(): Int = super.hashCode()

  def register(user: User, versionOpt: Option[String], lang: Option[String]) = {
    val id = user.id
    val email = user.email
    val realName = user.realName
    val nationalId = user.nationalId
    val password = user.password
    val referralParams = user.referedToken match {
      case Some(token) => try { Some(ReferralParams(Some(token.toLong))) } catch { case _: Throwable => None }
      case None => None
    }

    val profile = UserProfile(
      id = id,
      email = email,
      realName = realName,
      nationalId = nationalId,
      passwordHash = Some(password),
      emailVerified = false,
      mobile = None,
      mobileVerified = false,
      passwordResetToken = None,
      verificationToken = None,
      loginToken = None,
      googleAuthenticatorSecret = None,
      status = UserStatus.Normal,
      depositAddresses = None,
      withdrawalAddresses = None
    )

    val command = DoRegisterUser(profile, password, referralParams, versionOpt, lang)

    backend ? command map {
      case succeeded: RegisterUserSucceeded =>
        val returnProfile = succeeded.userProfile
        ApiResult(true, 0, returnProfile.id.toString)
      case failed: RegisterUserFailed =>
        ApiResult(false, failed.error.value, failed.toString)
      case x =>
        ApiResult(false, -1, x.toString)
    }
  }

  def getProfile(userId: Long) = {
    val command = QueryProfile(uid = Some(userId))
    backend ? command map {
      case result: QueryProfileResult =>
        result.userProfile match {
          case Some(profile) =>
            ApiResult(true, 0, "", Some(fromProfile(profile)))
          case None =>
            ApiResult(false, ErrorCode.UserNotExist.value, "用户不存在", None)
        }
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }

  def getProfileApiV2(userId: Long) = {
    val command = QueryProfile(uid = Some(userId))
    backend ? command map {
      case result: QueryProfileResult =>
        result.userProfile match {
          case Some(profile) =>
            ApiResult(true, 0, "", Some(apiV2FromProfile(profile, Seq.empty)))
          case None =>
            ApiResult(false, ErrorCode.UserNotExist.value, "用户不存在", None)
        }
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }

  def getApiSecret(userId: Long) = {
    val command = QueryApiSecrets(userId, None)
    backend ? command map {
      case QueryApiSecretsResult(userId, secrets) if secrets.nonEmpty =>
        val secret = secrets.head.secret
        ApiResult(true, 0, "", Some(secret))
      case _ => ApiResult(true, 0, "", None)
    }
  }

  def getAllApiSecrets(userId: Long) = {
    val command = QueryApiSecrets(userId, None)
    backend ? command map {
      case QueryApiSecretsResult(userId, secrets) if secrets.nonEmpty =>
        ApiResult(true, 0, "", Some(secrets))
      case _ => ApiResult(false, 0, "no api created", None)
    }
  }

  def getApiSecret(token: String) = {
    val command = QueryApiSecretByToken(token)
    backend ? command map {
      case QueryApiSecretByTokenResult(secret) if secret.isDefined =>
        ApiResult(true, 0, "", secret)
      case _ => ApiResult(false, 1002, "not found secret by this token", None)
    }
  }

  def generateApiSecret(userId: Long) = {
    val command = DoAddNewApiSecret(userId)
    backend ? command map {
      case ApiSecretOperationResult(error, secrets, secretOpt) if secretOpt.isDefined =>
        ApiResult(true, 0, "", secretOpt)
      case _ => ApiResult(false, -1, "", None)
    }
  }

  def deleteApiSecret(userId: Long, token: String) = {
    val command = DoDeleteApiSecret(ApiSecret("", Some(token), Some(userId), None))
    backend ? command map {
      case ApiSecretOperationResult(code, _, secretOpt) =>
        if (ErrorCode.Ok == code) ApiResult(true, 0, "", secretOpt)
        else ApiResult(false, code.value, "")
      case x =>
        ApiResult(false, -1, x.toString)
    }
  }

  def getDepositAddress(currencySeq: Seq[Currency], userId: Long) = {
    val cryptoCurrencySeq = currencySeq.filter(_.value >= 1000)
    backend ? QueryProfile(Some(userId)) map {
      case qpr: QueryProfileResult =>
        val addr = qpr.userProfile match {
          case Some(profile) =>
            // allocate new address
            val map: Map[Currency, String] = if (!profile.depositAddresses.isDefined) {
              getDepositAddressFromBackend(cryptoCurrencySeq, userId)
            } else {
              val curSeq = profile.depositAddresses.get.map { pair =>
                if (pair._1 == Currency.Nxt && pair._2.startsWith("//NXT")) null
                else if (pair._2 != "") pair._1
                else null
              }.filter(_ != null)
              val currencyDiff = cryptoCurrencySeq.diff(curSeq.toSeq)
              val mapFromBackend = getDepositAddressFromBackend(currencyDiff, userId)
              (profile.depositAddresses.get ++ mapFromBackend).toMap
            }

            setDepositAddressToBackend(profile, map)
            map
          case None => Map.empty[Currency, String]
        }

        val rv = cryptoCurrencySeq.map { c =>
          val s: String = c
          s -> addr.getOrElse(c, "")
        }.toMap

        ApiResult(true, 0, "", Some(rv))
      case x => ApiResult(false, -1, x.toString)
    }
  }

  private def getDepositAddressFromBackend(currencySeq: Seq[Currency], userId: Long): Map[Currency, String] = {
    val ListOfFuture = currencySeq.map { c =>
      backend ? AllocateNewAddress(c, userId) map {
        case rv: AllocateNewAddressResult =>
          val addr =
            if (rv.currency == Currency.Nxt) rv.address.getOrElse("") + "//" + rv.nxtRsAddress.getOrElse("") + "//" + rv.nxtPublicKey.getOrElse("")
            else rv.address.getOrElse("")
          (rv.currency, addr)
      }
    }
    val futureList = scala.concurrent.Future.sequence(ListOfFuture)
    Await.result(futureList.map(x => x.toMap), 3 second)
  }

  def setDepositAddressToBackend(profile: UserProfile, addrMap: Map[Currency, String]) = {
    val newProfile = profile.copy(depositAddresses = Some(addrMap))
    backend ! DoUpdateUserProfile(newProfile)
  }

  def setWithdrawalAddress(uid: Long, currency: Currency, address: String) = {
    //update withdrawal address of user profile
    backend ? QueryProfile(Some(uid)) map {
      case qpr: QueryProfileResult =>
        val addr = qpr.userProfile match {
          case Some(profile) =>
            val addrMap = profile.withdrawalAddresses match {
              case Some(withdrawalMap) => withdrawalMap ++ Map(currency -> address)
              case None => Map(currency -> address)
            }
            val newProfile = profile.copy(withdrawalAddresses = Some(addrMap))
            backend ! DoUpdateUserProfile(newProfile)
          case None =>
        }
        ApiResult(true, 0, "", Some(addr))
      case x => ApiResult(false, -1, x.toString)
    }
  }

  def getGoogleAuth(uid: Long) = {
    backend ? QueryProfile(Some(uid)) map {
      case qpr: QueryProfileResult =>
        val secret = qpr.userProfile match {
          case Some(profile) =>
            profile.googleAuthenticatorSecret.getOrElse("")
          case None => ""
        }
        ApiResult(true, 0, "", Some(secret))
      case x => ApiResult(false, -1, x.toString)
    }
  }

  def bindGoogleAuth(uid: Long, key: String) = {
    //update withdrawal address of user profile
    backend ? QueryProfile(Some(uid)) map {
      case qpr: QueryProfileResult =>
        qpr.userProfile match {
          case Some(profile) =>
            val newPro = profile.copy(googleAuthenticatorSecret = Some(key))
            backend ! DoUpdateUserProfile(newPro)
          case None =>
        }
        ApiResult(true, 0, "", None)
      case x => ApiResult(false, -1, x.toString)
    }
  }

  def unbindGoogleAuth(uid: Long) = {
    //update withdrawal address of user profile
    backend ? QueryProfile(Some(uid)) map {
      case qpr: QueryProfileResult =>
        qpr.userProfile match {
          case Some(profile) =>
            val newPro = profile.copy(googleAuthenticatorSecret = None)
            backend ! DoUpdateUserProfile(newPro)
          case None =>
        }
        ApiResult(true, 0, "", None)
      case x => ApiResult(false, -1, x.toString)
    }
  }

  def getWithdrawalAddress(userId: Long, currency: Currency) = {
    backend ? QueryProfile(Some(userId)) map {
      case qpr: QueryProfileResult =>
        val addr = qpr.userProfile match {
          case Some(profile) =>
            profile.withdrawalAddresses match {
              case Some(addressMap) =>
                addressMap.get(currency) match {
                  case Some(address) => address
                  case None => ""
                }
              case None => ""
            }
          case None => ""
        }
        ApiResult(true, 0, "", Some(addr))
      case None => ApiResult(false, 1, "", Some(""))
    }
  }

  def updateProfile(user: User) = {
    val id = user.id
    val email = user.email
    val realName = user.realName
    val nationalId = user.nationalId
    val mobile = user.mobile
    val depositAddr = user.depositAddress
    val withdrawalAddr = user.withdrawalAddress

    val profile = UserProfile(
      id = id,
      email = email,
      realName = realName,
      nationalId = nationalId,
      emailVerified = true,
      mobile = mobile,
      mobileVerified = true,
      status = UserStatus.Normal,
      depositAddresses = depositAddr,
      withdrawalAddresses = withdrawalAddr)

    val command = DoUpdateUserProfile(profile)
    backend ? command map {
      case succeeded: UpdateUserProfileSucceeded =>
        val returnProfile = succeeded.userProfile
        ApiResult(true, 0, returnProfile.id.toString)
      case failed: UpdateUserProfileFailed =>
        ApiResult(false, failed.error.value, failed.toString)
      case x =>
        ApiResult(false, -1, x.toString)
    }
  }

  def verifyRealName(userId: Long, realName: String, location: String, identiType: String, idNumber: String) = {
    val command = DoVerifyRealName(userId, realName, location, identiType, idNumber)
    backend ? command map {
      case succeeded: VerifyRealNameSucceeded =>
        val returnProfile = succeeded.userProfile
        ApiResult(true, 0, "")
      case failed: VerifyRealNameFailed =>
        ApiResult(false, failed.error.value, failed.toString)
      case x =>
        ApiResult(false, -1, x.toString)
    }
  }

  def addBankCard(userId: Long, bankName: String, ownerName: String, cardNumber: String, branchBankName: String) = {
    val branchOpt = if (branchBankName != null && branchBankName.trim.nonEmpty) Some(branchBankName) else None
    val bankCard = BankCard(bankName, ownerName, cardNumber, branchOpt)
    val command = DoAddBankCard(userId, bankCard)
    backend ? command map {
      case succeeded: AddBankCardSucceeded =>
        ApiResult(true, 0, "")
      case failed: AddBankCardFailed =>
        ApiResult(false, failed.error.value, failed.toString)
      case x =>
        ApiResult(false, -1, x.toString)
    }
  }

  def deleteBankCard(userId: Long, cardNumber: String) = {
    val command = DoDeleteBankCard(userId, cardNumber)
    backend ? command map {
      case succeeded: DeleteBankCardSucceeded =>
        ApiResult(true, 0, "")
      case failed: DeleteBankCardFailed =>
        ApiResult(false, failed.error.value, failed.toString)
      case x =>
        ApiResult(false, -1, x.toString)
    }
  }

  def queryBankCards(uid: Long) = {
    val command = QueryProfile(uid = Some(uid))
    backend ? command map {
      case result: QueryProfileResult =>
        val bankCards = result.userProfile.map(_.bankCards.getOrElse(Seq[BankCard]()))
        ApiResult(true, 0, "", bankCards)
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }

  def login(user: User) = {
    val email = user.email
    val password = user.password

    val command = Login(email, password)

    backend ? command map {
      case succeeded: LoginSucceeded =>
        val profile = succeeded.userProfile
        val user = User(id = profile.id, email = profile.email, realName = profile.realName, password = "", mobile = profile.mobile, googleAuthenticatorSecret = profile.googleAuthenticatorSecret, securityPreference = profile.securityPreference, realName2 = profile.realName2)
        ApiResult(true, 0, "登录成功", Some(user))
      case failed: LoginFailed =>
        ApiResult(false, failed.error.value, failed.toString)
      case x =>
        ApiResult(false, -1, x.toString)
    }
  }

  def verifyEmail(token: String) = {
    val command = VerifyEmail(token)
    backend ? command map {
      case succeeded: VerifyEmailSucceeded =>
        ApiResult(true, 0, "注册邮箱验证成功", Some(succeeded))
      case failed: VerifyEmailFailed =>
        ApiResult(false, failed.error.value, failed.toString)
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }

  def requestPasswordReset(email: String, versionOpt: Option[String], lang: Option[String]) = {
    val command = DoRequestPasswordReset(email, None, versionOpt, lang)
    backend ? command map {
      case succeeded: RequestPasswordResetSucceeded =>
        ApiResult(true, 0, "重置密码链接已发送，请查看注册邮箱", Some(succeeded))
      case failed: RequestPasswordResetFailed =>
        ApiResult(false, failed.error.value, failed.toString)
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }

  def validatePasswordResetToken(token: String) = {
    val command = ValidatePasswordResetToken(token)
    backend ? command map {
      case result: PasswordResetTokenValidationResult =>
        result.userProfile match {
          case Some(profile) => ApiResult(true, 0, "")
          case None => ApiResult(false, -1, "")
        }
    }
  }

  def resetPassword(newPassword: String, token: String) = {
    val command = DoResetPassword(newPassword, token)
    backend ? command map {
      case succeeded: ResetPasswordSucceeded =>
        ApiResult(true, 0, "", Some(succeeded))
      case failed: ResetPasswordFailed =>
        ApiResult(false, failed.error.value, failed.toString)
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }

  def changePassword(email: String, oldPassword: String, newPassword: String) = {
    val command = DoChangePassword(email, oldPassword, newPassword)
    backend ? command map {
      case succeeded: DoChangePasswordSucceeded =>
        ApiResult(true, 0, "")
      case failed: DoChangePasswordFailed =>
        ApiResult(false, failed.error.value, failed.toString)
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }

  def bindOrUpdateMobile(email: String, newMobile: String) = {
    val command = DoBindMobile(email, newMobile)
    backend ? command map {
      case succeeded: DoBindMobileSucceeded =>
        ApiResult(true, 0, "")
      case failed: DoBindMobileFailed =>
        ApiResult(false, failed.error.value, failed.toString)
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }

  def resendVerifyEmail(email: String, versionOpt: Option[String], lang: Option[String]) = {
    val command = DoResendVerifyEmail(email, versionOpt, lang)
    backend ? command map {
      case result: ResendVerifyEmailSucceeded =>
        ApiResult(true, 0, "")
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }

  def queryUserProfileByEmail(email: String) = {
    val command = QueryProfile(email = Some(email))
    backend ? command map {
      case result: QueryProfileResult =>
        ApiResult(true, 0, "", result.userProfile)
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }

  def queryUserProfileById(uid: Long) = {
    val command = QueryProfile(uid = Some(uid))
    backend ? command map {
      case result: QueryProfileResult =>
        ApiResult(true, 0, "", result.userProfile)
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }

  def sendVerificationCodeEmail(email: String, code: String, versionOpt: Option[String], lang: Option[String]) = {
    backend ? DoSendVerificationCodeEmail(email, code, versionOpt, lang) map {
      case result: SendVerificationCodeEmailSucceeded =>
        ApiResult(true, 0, "")
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }

  def setUserSecurityPreference(uid: Long, preference: String) = {
    //update withdrawal address of user profile
    backend ? QueryProfile(Some(uid)) map {
      case qpr: QueryProfileResult =>
        qpr.userProfile match {
          case Some(profile) =>
            val newPro = profile.copy(securityPreference = Some(preference))
            backend ! DoUpdateUserProfile(newPro)
          case None =>
        }
        ApiResult(true, 0, "", None)
      case x => ApiResult(false, -1, x.toString)
    }
  }

  def updateNickName(uid: Long, nickname: String) = {
    backend ? QueryProfile(Some(uid)) map {
      case qpr: QueryProfileResult =>
        qpr.userProfile match {
          case Some(profile) =>
            val newPro = profile.copy(realName = Some(nickname))
            backend ! DoUpdateUserProfile(newPro)
            ApiResult(true, 0, "", None)
          case None => ApiResult(false, ErrorCode.UserNotExist.value, "user not exist")
        }
      case x => ApiResult(false, -1, x.toString)
    }
  }

  def sendEdm(email: String, tpl: String) = {
    backend ? DoSendEmail(email, EmailType.Others, null, tplName = Some(tpl)) map {
      case r => ApiResult(true, 0, "", None)
    }
  }
}
