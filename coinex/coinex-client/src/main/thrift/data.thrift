/**
 * Copyright {C} 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

namespace java com.coinport.coinex.data

///////////////////////////////////////////////////////////////////////
///////////////////////////// ERROR CODES /////////////////////////////
///////////////////////////////////////////////////////////////////////

enum ErrorCode {
    OK = 0

    // User related
    EMAIL_ALREADY_REGISTERED         = 1001
    MISSING_INFORMATION              = 1002
    USER_NOT_EXIST                   = 1003
    PASSWORD_NOT_MATCH               = 1004
    TOKEN_NOT_MATCH                  = 1005
    TOKEN_NOT_UNIQUE                 = 1006

    // Account related
    PRICE_OUT_OF_RANGE               = 2001
    INSUFFICIENT_FUND                = 2002
    INVALID_AMOUNT                   = 2003

    // Market related
    ORDER_NOT_EXIST                  = 3001

    // Api Auth related
    TOO_MANY_SECRETS                 = 5001
    INVALID_SECRET                   = 5002

    // Deposit/Withdrawal
    ALREADY_CONFIRMED                = 6001
    DEPOSIT_NOT_EXIST                = 6002
    WITHDRAWAL_NOT_EXIST             = 6003
}


///////////////////////////////////////////////////////////////////////
///////////////////////// PERSISTENT ENUMS ////////////////////////////
///////////////////////////////////////////////////////////////////////
enum Currency {
    UNKNOWN = 0
    RMB = 1
    USD = 2
    BTC = 1001
    LTC = 1002
    PTS = 1003
}

enum OrderStatus {
    PENDING = 0
    PARTIALLY_EXECUTED = 1
    FULLY_EXECUTED = 2
    CANCELLED = 3
    MARKET_AUTO_CANCELLED = 4
    MARKET_AUTO_PARTIALLY_CANCELLED = 5
}

enum UserStatus {
    NORMAL = 0
    SUSPENDED = 1
}

enum EmailType {
    REGISTER_VERIFY = 1
    LOGIN_TOKEN = 2
    PASSWORD_RESET_TOKEN = 3
}

enum ChartTimeDimension {
    ONE_MINUTE = 1
    THREE_MINUTES = 2
    FIVE_MINUTES = 3
    FIFTEEN_MINUTES = 4
    THIRTY_MINUTES = 5
    ONE_HOUR = 6
    TWO_HOURS = 7
    FOUR_HOURS = 8
    SIX_HOURS = 9
    TWELVE_HOURS = 10
    ONE_DAY = 11
    THREE_DAYS = 12
    ONE_WEEK = 13
}

enum Direction {
    UP = 1
    DOWN = 2
    KEEP = 3
}

enum TransferStatus {
    PENDING = 0
    SUCCEEDED = 1
    FAILED = 2
}

///////////////////////////////////////////////////////////////////////
////////////////////////// PERSISTENT DATA ////////////////////////////
///////////////////////////////////////////////////////////////////////
struct UserProfile {
    1:  i64 id
    2:  string email
    3:  optional string realName
    4:  optional string nationalId
    5:  optional string passwordHash
    6:  bool emailVerified
    8:  optional string mobile
    9:  bool mobileVerified
    10: optional string passwordResetToken
    11: optional string verificationToken
    12: optional string loginToken
    13: optional string googleAuthenticatorSecret
    14: UserStatus status
}

struct MarketSide {
    1: Currency outCurrency
    2: Currency inCurrency
}

struct Fee {
    1: i64 payer
    2: optional i64 payee  // pay to coinport if None
    3: Currency currency
    4: i64 amount
    5: optional string basis
}

struct Order {
    1: i64 userId
    2: i64 id
    3: i64 quantity
    4: optional double price
    5: optional i64 takeLimit
    6: optional i64 timestamp
    7: optional i32 robotType
    8: optional i64 robotId
    9: optional bool onlyTaker
    10: i64 inAmount = 0
}

struct OrderInfo {
    1: MarketSide side
    2: Order order
    3: i64 outAmount
    4: i64 inAmount
    5: OrderStatus status
    6: optional i64 lastTxTimestamp
}

struct OrderUpdate {
    1: Order previous
    2: Order current
}

struct Transaction {
    1: i64 id
    2: i64 timestamp
    3: MarketSide side
    4: OrderUpdate takerUpdate
    5: OrderUpdate makerUpdate
    6: optional list<Fee> fees
}

struct CashAccount {
    1: Currency currency
    2: i64 available
    3: i64 locked
    4: i64 pendingWithdrawal
}

struct UserAccount {
    1: i64 userId
    2: map<Currency, CashAccount> cashAccounts
}

struct UserLogsState {
    1: map<i64, list<OrderInfo>> orderInfoMap
}

struct MarketDepthItem {
    1: double price
    2: i64 quantity
}

struct MarketDepth {
    1: MarketSide side
    2: list<MarketDepthItem> asks
    3: list<MarketDepthItem> bids
}

struct CandleDataItem {
    1: i64 timestamp
    2: i64 volumn
    3: double open
    4: double close
    5: double low
    6: double high
}

struct CandleData {
    1: i64 timestamp
    2: list<CandleDataItem> items
    3: MarketSide side
}

struct MetricsByMarket {
    1: MarketSide side
    2: double price  // 当前价格

    // ------------- 一段时间内（24 小时） ----------
    3: optional double low
    4: optional double high
    5: i64 volume = 0
    6: optional double gain  // 涨幅百分比

    7: Direction direction = Direction.KEEP
}

struct Metrics {
    1: map<MarketSide, MetricsByMarket> metricsByMarket
}

struct ApiSecret {
    1: string secret
    2: optional string identifier
    3: optional i64 userId
}

struct Deposit {
    1: i64 id
    2: i64 userId
    3: Currency currency
    4: i64 amount
    5: TransferStatus status = TransferStatus.PENDING
    6: optional i64 created
    7: optional i64 updated
    8: optional ErrorCode reason
    9: optional Fee fee
}

struct Withdrawal {
    1: i64 id
    2: i64 userId
    3: Currency currency
    4: i64 amount
    5: TransferStatus status
    6: optional i64 created
    7: optional i64 updated
    8: optional ErrorCode reason
    9: optional Fee fee
}

struct Cursor {
    1: i32 skip
    2: i32 limit
}

struct SpanCursor {
    1: i64 from
    2: i64 to
}

struct TransactionItem  {
    1: i64 tid
    2: double price
    3: i64 volume
    4: i64 amount
    5: i64 taker
    6: i64 maker
    7: i64 tOrder
    8: i64 mOrder
    9: MarketSide side
    10: i64 timestamp
}

struct RedeliverFilterData {
    1: list<i64> ids
}