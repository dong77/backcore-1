
/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * This file was auto generated by auto_gen_serializer.sh on Sat Mar 29 14:16:16 CST 2014
 */

package com.coinport.coinex.serializers

import akka.serialization.Serializer
import com.twitter.bijection.scrooge.BinaryScalaCodec
import com.coinport.coinex.data._

class ThriftBinarySerializer extends Serializer {
  val includeManifest: Boolean = true
  val identifier = 607870725
  lazy val s_0 = BinaryScalaCodec(AccountOperationResult)
  lazy val s_1 = BinaryScalaCodec(ApiSecret)
  lazy val s_2 = BinaryScalaCodec(ApiSecretOperationResult)
  lazy val s_3 = BinaryScalaCodec(ApiSecretState)
  lazy val s_4 = BinaryScalaCodec(CandleData)
  lazy val s_5 = BinaryScalaCodec(CandleDataItem)
  lazy val s_6 = BinaryScalaCodec(CashAccount)
  lazy val s_7 = BinaryScalaCodec(DoAddNewApiSecret)
  lazy val s_8 = BinaryScalaCodec(DoCancelOrder)
  lazy val s_9 = BinaryScalaCodec(DoConfirmCashWithdrawalFailed)
  lazy val s_10 = BinaryScalaCodec(DoConfirmCashWithdrawalSuccess)
  lazy val s_11 = BinaryScalaCodec(DoDeleteApiSecret)
  lazy val s_12 = BinaryScalaCodec(DoDepositCash)
  lazy val s_13 = BinaryScalaCodec(DoRegisterUser)
  lazy val s_14 = BinaryScalaCodec(DoRequestCashWithdrawal)
  lazy val s_15 = BinaryScalaCodec(DoRequestPasswordReset)
  lazy val s_16 = BinaryScalaCodec(DoResetPassword)
  lazy val s_17 = BinaryScalaCodec(DoSubmitOrder)
  lazy val s_18 = BinaryScalaCodec(DoUpdateMetrics)
  lazy val s_19 = BinaryScalaCodec(Login)
  lazy val s_20 = BinaryScalaCodec(LoginFailed)
  lazy val s_21 = BinaryScalaCodec(LoginSucceeded)
  lazy val s_22 = BinaryScalaCodec(MarketByMetrics)
  lazy val s_23 = BinaryScalaCodec(MarketDepth)
  lazy val s_24 = BinaryScalaCodec(MarketDepthItem)
  lazy val s_25 = BinaryScalaCodec(MarketSide)
  lazy val s_26 = BinaryScalaCodec(Order)
  lazy val s_27 = BinaryScalaCodec(OrderCancelled)
  lazy val s_28 = BinaryScalaCodec(OrderCashLocked)
  lazy val s_29 = BinaryScalaCodec(OrderInfo)
  lazy val s_30 = BinaryScalaCodec(OrderSubmissionFailed)
  lazy val s_31 = BinaryScalaCodec(OrderSubmitted)
  lazy val s_32 = BinaryScalaCodec(OrderUpdate)
  lazy val s_33 = BinaryScalaCodec(PersistentAccountState)
  lazy val s_34 = BinaryScalaCodec(QueryAccount)
  lazy val s_35 = BinaryScalaCodec(QueryAccountResult)
  lazy val s_36 = BinaryScalaCodec(QueryApiSecrets)
  lazy val s_37 = BinaryScalaCodec(QueryApiSecretsResult)
  lazy val s_38 = BinaryScalaCodec(QueryCandleData)
  lazy val s_39 = BinaryScalaCodec(QueryCandleDataResult)
  lazy val s_40 = BinaryScalaCodec(QueryMarket)
  lazy val s_41 = BinaryScalaCodec(QueryMarketResult)
  lazy val s_42 = BinaryScalaCodec(QueryMarketUnsupportedMarketFailure)
  lazy val s_43 = BinaryScalaCodec(QueryTransactionData)
  lazy val s_44 = BinaryScalaCodec(QueryTransactionDataResult)
  lazy val s_45 = BinaryScalaCodec(QueryUserOrders)
  lazy val s_46 = BinaryScalaCodec(QueryUserOrdersResult)
  lazy val s_47 = BinaryScalaCodec(QueryUserTransaction)
  lazy val s_48 = BinaryScalaCodec(QueryUserTransactionResult)
  lazy val s_49 = BinaryScalaCodec(RegisterUserFailed)
  lazy val s_50 = BinaryScalaCodec(RegisterUserSucceeded)
  lazy val s_51 = BinaryScalaCodec(RequestPasswordResetFailed)
  lazy val s_52 = BinaryScalaCodec(RequestPasswordResetSucceeded)
  lazy val s_53 = BinaryScalaCodec(ResetPasswordFailed)
  lazy val s_54 = BinaryScalaCodec(ResetPasswordSucceeded)
  lazy val s_55 = BinaryScalaCodec(RobotMetrics)
  lazy val s_56 = BinaryScalaCodec(SendMailRequest)
  lazy val s_57 = BinaryScalaCodec(Transaction)
  lazy val s_58 = BinaryScalaCodec(TransactionData)
  lazy val s_59 = BinaryScalaCodec(TransactionItem)
  lazy val s_60 = BinaryScalaCodec(UserAccount)
  lazy val s_61 = BinaryScalaCodec(UserLogsState)
  lazy val s_62 = BinaryScalaCodec(UserProfile)
  lazy val s_63 = BinaryScalaCodec(ValidatePasswordResetToken)
  lazy val s_64 = BinaryScalaCodec(ValidatePasswordResetTokenResult)

  def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case m: AccountOperationResult => s_0(m)
    case m: ApiSecret => s_1(m)
    case m: ApiSecretOperationResult => s_2(m)
    case m: ApiSecretState => s_3(m)
    case m: CandleData => s_4(m)
    case m: CandleDataItem => s_5(m)
    case m: CashAccount => s_6(m)
    case m: DoAddNewApiSecret => s_7(m)
    case m: DoCancelOrder => s_8(m)
    case m: DoConfirmCashWithdrawalFailed => s_9(m)
    case m: DoConfirmCashWithdrawalSuccess => s_10(m)
    case m: DoDeleteApiSecret => s_11(m)
    case m: DoDepositCash => s_12(m)
    case m: DoRegisterUser => s_13(m)
    case m: DoRequestCashWithdrawal => s_14(m)
    case m: DoRequestPasswordReset => s_15(m)
    case m: DoResetPassword => s_16(m)
    case m: DoSubmitOrder => s_17(m)
    case m: DoUpdateMetrics => s_18(m)
    case m: Login => s_19(m)
    case m: LoginFailed => s_20(m)
    case m: LoginSucceeded => s_21(m)
    case m: MarketByMetrics => s_22(m)
    case m: MarketDepth => s_23(m)
    case m: MarketDepthItem => s_24(m)
    case m: MarketSide => s_25(m)
    case m: Order => s_26(m)
    case m: OrderCancelled => s_27(m)
    case m: OrderCashLocked => s_28(m)
    case m: OrderInfo => s_29(m)
    case m: OrderSubmissionFailed => s_30(m)
    case m: OrderSubmitted => s_31(m)
    case m: OrderUpdate => s_32(m)
    case m: PersistentAccountState => s_33(m)
    case m: QueryAccount => s_34(m)
    case m: QueryAccountResult => s_35(m)
    case m: QueryApiSecrets => s_36(m)
    case m: QueryApiSecretsResult => s_37(m)
    case m: QueryCandleData => s_38(m)
    case m: QueryCandleDataResult => s_39(m)
    case m: QueryMarket => s_40(m)
    case m: QueryMarketResult => s_41(m)
    case m: QueryMarketUnsupportedMarketFailure => s_42(m)
    case m: QueryTransactionData => s_43(m)
    case m: QueryTransactionDataResult => s_44(m)
    case m: QueryUserOrders => s_45(m)
    case m: QueryUserOrdersResult => s_46(m)
    case m: QueryUserTransaction => s_47(m)
    case m: QueryUserTransactionResult => s_48(m)
    case m: RegisterUserFailed => s_49(m)
    case m: RegisterUserSucceeded => s_50(m)
    case m: RequestPasswordResetFailed => s_51(m)
    case m: RequestPasswordResetSucceeded => s_52(m)
    case m: ResetPasswordFailed => s_53(m)
    case m: ResetPasswordSucceeded => s_54(m)
    case m: RobotMetrics => s_55(m)
    case m: SendMailRequest => s_56(m)
    case m: Transaction => s_57(m)
    case m: TransactionData => s_58(m)
    case m: TransactionItem => s_59(m)
    case m: UserAccount => s_60(m)
    case m: UserLogsState => s_61(m)
    case m: UserProfile => s_62(m)
    case m: ValidatePasswordResetToken => s_63(m)
    case m: ValidatePasswordResetTokenResult => s_64(m)

    case m => throw new IllegalArgumentException("Cannot serialize object: " + m)
  }

  def fromBinary(bytes: Array[Byte],
    clazz: Option[Class[_]]): AnyRef = clazz match {
    case Some(c) if c == classOf[AccountOperationResult.Immutable] => s_0.invert(bytes).get
    case Some(c) if c == classOf[ApiSecret.Immutable] => s_1.invert(bytes).get
    case Some(c) if c == classOf[ApiSecretOperationResult.Immutable] => s_2.invert(bytes).get
    case Some(c) if c == classOf[ApiSecretState.Immutable] => s_3.invert(bytes).get
    case Some(c) if c == classOf[CandleData.Immutable] => s_4.invert(bytes).get
    case Some(c) if c == classOf[CandleDataItem.Immutable] => s_5.invert(bytes).get
    case Some(c) if c == classOf[CashAccount.Immutable] => s_6.invert(bytes).get
    case Some(c) if c == classOf[DoAddNewApiSecret.Immutable] => s_7.invert(bytes).get
    case Some(c) if c == classOf[DoCancelOrder.Immutable] => s_8.invert(bytes).get
    case Some(c) if c == classOf[DoConfirmCashWithdrawalFailed.Immutable] => s_9.invert(bytes).get
    case Some(c) if c == classOf[DoConfirmCashWithdrawalSuccess.Immutable] => s_10.invert(bytes).get
    case Some(c) if c == classOf[DoDeleteApiSecret.Immutable] => s_11.invert(bytes).get
    case Some(c) if c == classOf[DoDepositCash.Immutable] => s_12.invert(bytes).get
    case Some(c) if c == classOf[DoRegisterUser.Immutable] => s_13.invert(bytes).get
    case Some(c) if c == classOf[DoRequestCashWithdrawal.Immutable] => s_14.invert(bytes).get
    case Some(c) if c == classOf[DoRequestPasswordReset.Immutable] => s_15.invert(bytes).get
    case Some(c) if c == classOf[DoResetPassword.Immutable] => s_16.invert(bytes).get
    case Some(c) if c == classOf[DoSubmitOrder.Immutable] => s_17.invert(bytes).get
    case Some(c) if c == classOf[DoUpdateMetrics.Immutable] => s_18.invert(bytes).get
    case Some(c) if c == classOf[Login.Immutable] => s_19.invert(bytes).get
    case Some(c) if c == classOf[LoginFailed.Immutable] => s_20.invert(bytes).get
    case Some(c) if c == classOf[LoginSucceeded.Immutable] => s_21.invert(bytes).get
    case Some(c) if c == classOf[MarketByMetrics.Immutable] => s_22.invert(bytes).get
    case Some(c) if c == classOf[MarketDepth.Immutable] => s_23.invert(bytes).get
    case Some(c) if c == classOf[MarketDepthItem.Immutable] => s_24.invert(bytes).get
    case Some(c) if c == classOf[MarketSide.Immutable] => s_25.invert(bytes).get
    case Some(c) if c == classOf[Order.Immutable] => s_26.invert(bytes).get
    case Some(c) if c == classOf[OrderCancelled.Immutable] => s_27.invert(bytes).get
    case Some(c) if c == classOf[OrderCashLocked.Immutable] => s_28.invert(bytes).get
    case Some(c) if c == classOf[OrderInfo.Immutable] => s_29.invert(bytes).get
    case Some(c) if c == classOf[OrderSubmissionFailed.Immutable] => s_30.invert(bytes).get
    case Some(c) if c == classOf[OrderSubmitted.Immutable] => s_31.invert(bytes).get
    case Some(c) if c == classOf[OrderUpdate.Immutable] => s_32.invert(bytes).get
    case Some(c) if c == classOf[PersistentAccountState.Immutable] => s_33.invert(bytes).get
    case Some(c) if c == classOf[QueryAccount.Immutable] => s_34.invert(bytes).get
    case Some(c) if c == classOf[QueryAccountResult.Immutable] => s_35.invert(bytes).get
    case Some(c) if c == classOf[QueryApiSecrets.Immutable] => s_36.invert(bytes).get
    case Some(c) if c == classOf[QueryApiSecretsResult.Immutable] => s_37.invert(bytes).get
    case Some(c) if c == classOf[QueryCandleData.Immutable] => s_38.invert(bytes).get
    case Some(c) if c == classOf[QueryCandleDataResult.Immutable] => s_39.invert(bytes).get
    case Some(c) if c == classOf[QueryMarket.Immutable] => s_40.invert(bytes).get
    case Some(c) if c == classOf[QueryMarketResult.Immutable] => s_41.invert(bytes).get
    case Some(c) if c == classOf[QueryMarketUnsupportedMarketFailure.Immutable] => s_42.invert(bytes).get
    case Some(c) if c == classOf[QueryTransactionData.Immutable] => s_43.invert(bytes).get
    case Some(c) if c == classOf[QueryTransactionDataResult.Immutable] => s_44.invert(bytes).get
    case Some(c) if c == classOf[QueryUserOrders.Immutable] => s_45.invert(bytes).get
    case Some(c) if c == classOf[QueryUserOrdersResult.Immutable] => s_46.invert(bytes).get
    case Some(c) if c == classOf[QueryUserTransaction.Immutable] => s_47.invert(bytes).get
    case Some(c) if c == classOf[QueryUserTransactionResult.Immutable] => s_48.invert(bytes).get
    case Some(c) if c == classOf[RegisterUserFailed.Immutable] => s_49.invert(bytes).get
    case Some(c) if c == classOf[RegisterUserSucceeded.Immutable] => s_50.invert(bytes).get
    case Some(c) if c == classOf[RequestPasswordResetFailed.Immutable] => s_51.invert(bytes).get
    case Some(c) if c == classOf[RequestPasswordResetSucceeded.Immutable] => s_52.invert(bytes).get
    case Some(c) if c == classOf[ResetPasswordFailed.Immutable] => s_53.invert(bytes).get
    case Some(c) if c == classOf[ResetPasswordSucceeded.Immutable] => s_54.invert(bytes).get
    case Some(c) if c == classOf[RobotMetrics.Immutable] => s_55.invert(bytes).get
    case Some(c) if c == classOf[SendMailRequest.Immutable] => s_56.invert(bytes).get
    case Some(c) if c == classOf[Transaction.Immutable] => s_57.invert(bytes).get
    case Some(c) if c == classOf[TransactionData.Immutable] => s_58.invert(bytes).get
    case Some(c) if c == classOf[TransactionItem.Immutable] => s_59.invert(bytes).get
    case Some(c) if c == classOf[UserAccount.Immutable] => s_60.invert(bytes).get
    case Some(c) if c == classOf[UserLogsState.Immutable] => s_61.invert(bytes).get
    case Some(c) if c == classOf[UserProfile.Immutable] => s_62.invert(bytes).get
    case Some(c) if c == classOf[ValidatePasswordResetToken.Immutable] => s_63.invert(bytes).get
    case Some(c) if c == classOf[ValidatePasswordResetTokenResult.Immutable] => s_64.invert(bytes).get

    case Some(c) => throw new IllegalArgumentException("Cannot deserialize class: " + c.getCanonicalName)
    case None => throw new IllegalArgumentException("No class found in EventSerializer when deserializing array: " + bytes.mkString("").take(100))
  }
}
