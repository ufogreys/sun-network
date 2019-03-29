package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.FileUtil;
import org.tron.core.Constant;
import org.tron.core.Wallet;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.AssetIssueCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.Parameter;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract;
import org.tron.protos.Contract.AssetIssueContract.FrozenSupply;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Transaction.Result.code;

@Slf4j
public class AssetIssueActuatorTest {

  private static TronApplicationContext context;
  private static Manager dbManager;
  private static final String dbPath = "output_assetIssue_test";
  private static final String OWNER_ADDRESS;
  private static final String OWNER_ADDRESS_SECOND;
  private static final String NAME = "1";
  private static final long TOTAL_SUPPLY = 10000L;
  private static final int TRX_NUM = 10000;
  private static final int NUM = 100000;
  private static final String DESCRIPTION = "myCoin";
  private static final String URL = "tron-my.com";
  private static final String ASSET_NAME_SECOND = "asset_name2";
  private static long now = 0;
  private static long startTime = 0;
  private static long endTime = 0;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new TronApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049150";
    OWNER_ADDRESS_SECOND =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    //    Args.setParam(new String[]{"--output-directory", dbPath},
    //        "config-junit.conf");
    //    dbManager = new Manager();
    //    dbManager.init();
  }

  /**
   * create temp Capsule test need.
   */
  @Before
  public void createCapsule() {
    AccountCapsule ownerCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("owner"),
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
            AccountType.Normal,
            dbManager.getDynamicPropertiesStore().getAssetIssueFee());
    AccountCapsule ownerSecondCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("ownerSecond"),
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_SECOND)),
            AccountType.Normal,
            dbManager.getDynamicPropertiesStore().getAssetIssueFee());
    dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
    dbManager.getAccountStore().put(
        ownerSecondCapsule.getAddress().toByteArray(), ownerSecondCapsule);

    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(24 * 3600 * 1000);
//    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);

    now = dbManager.getHeadBlockTimeStamp();
    startTime = now + 48 * 3600 * 1000;
    endTime = now + 72 * 3600 * 1000;
  }

  @After
  public void removeCapsule() {
    byte[] address = ByteArray.fromHexString(OWNER_ADDRESS);
    dbManager.getAccountStore().delete(address);
  }

  /**
   * Release resources.
   */
  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  private Any getContract() {
    long nowTime = new Date().getTime();
    return Any.pack(
        Contract.AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
            .setNum(NUM)
            .setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .setPrecision(6)
            .build());
  }


  /**
   * SameTokenName open, asset issue success
   */
  @Test
  public void SameTokenNameOpenAssetIssueSuccess() {
//    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
    AssetIssueActuator actuator = new AssetIssueActuator(getContract(), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    Long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      //V2
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      byte[] assertKey = ByteArray.fromString(String.valueOf(tokenIdNum));
      AssetIssueCapsule assetIssueCapsuleV2 = dbManager.getAssetIssueV2Store().get(assertKey);
      Assert.assertNotNull(assetIssueCapsuleV2);
      Assert.assertEquals(6, assetIssueCapsuleV2.getPrecision());
      Assert.assertEquals(NUM, assetIssueCapsuleV2.getNum());
      Assert.assertEquals(TRX_NUM, assetIssueCapsuleV2.getTrxNum());
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          TOTAL_SUPPLY);

    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
    }
  }

  @Test
  /**
   Total supply must greater than zero.Else can't asset issue and balance do not change.
   */
  public void negativeTotalSupplyTest() {
    long nowTime = new Date().getTime();
    Any contract = Any.pack(
        Contract.AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME))
            .setTotalSupply(-TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
            .setNum(NUM)
            .setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .build());

    AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("TotalSupply must greater than 0!".equals(e.getMessage()));
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }
  }

  @Test
  /**
   Total supply must greater than zero.Else can't asset issue and balance do not change.
   */
  public void zeroTotalSupplyTest() {
    long nowTime = new Date().getTime();
    Any contract = Any.pack(
        Contract.AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME))
            .setTotalSupply(0)
            .setTrxNum(TRX_NUM)
            .setNum(NUM)
            .setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .build());

    AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("TotalSupply must greater than 0!".equals(e.getMessage()));
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }
  }

  @Test
  /*
    Trx num must greater than zero.Else can't asset issue and balance do not change.
   */
  public void negativeTrxNumTest() {
    long nowTime = new Date().getTime();
    Any contract = Any.pack(
        Contract.AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(-TRX_NUM)
            .setNum(NUM)
            .setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .build());

    AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("TrxNum must greater than 0!".equals(e.getMessage()));
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }
  }

  @Test
  /*
    Trx num must greater than zero.Else can't asset issue and balance do not change.
   */
  public void zeroTrxNumTest() {
    long nowTime = new Date().getTime();
    Any contract = Any.pack(
        Contract.AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(0)
            .setNum(NUM)
            .setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .build());

    AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("TrxNum must greater than 0!".equals(e.getMessage()));
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }
  }

  @Test
  /*
    Num must greater than zero.Else can't asset issue and balance do not change.
   */
  public void negativeNumTest() {
    long nowTime = new Date().getTime();
    Any contract = Any.pack(
        Contract.AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
            .setNum(-NUM)
            .setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .build());

    AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("Num must greater than 0!".equals(e.getMessage()));
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }
  }

  @Test
  /*
    Trx num must greater than zero.Else can't asset issue and balance do not change.
   */
  public void zeroNumTest() {
    long nowTime = new Date().getTime();
    Any contract = Any.pack(
        Contract.AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
            .setNum(0)
            .setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .build());

    AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("Num must greater than 0!".equals(e.getMessage()));
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }
  }

  @Test
  /*
   * Asset name length must between 1 to 32 and can not contain space and other unreadable character, and can not contain chinese characters.
   */
  public void assetNameTest() {
    long nowTime = new Date().getTime();

    //Empty name, throw exception
    Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.EMPTY)
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(nowTime)
        .setEndTime(nowTime + 24 * 3600 * 1000)
        .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());

    AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid assetName", e.getMessage());
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum+1)));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    //Too long name, throw exception. Max long is 32.
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8("testname0123456789abcdefghijgklmo"))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(nowTime)
        .setEndTime(nowTime + 24 * 3600 * 1000)
        .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid assetName", e.getMessage());
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum+1)));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    //Contain space, throw exception. Every character need readable .
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8("t e"))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(nowTime)
        .setEndTime(nowTime + 24 * 3600 * 1000)
        .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid assetName", e.getMessage());
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum+1)));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    //Contain chinese character, throw exception.
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFrom(ByteArray.fromHexString("E6B58BE8AF95")))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(nowTime)
        .setEndTime(nowTime + 24 * 3600 * 1000)
        .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid assetName", e.getMessage());
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum+1)));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    // 32 byte readable character just ok.
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8("testname0123456789abcdefghijgklm"))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(nowTime)
        .setEndTime(nowTime + 24 * 3600 * 1000)
        .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum+1)));
      Assert.assertNotNull(assetIssueCapsule);
      tokenIdNum ++;

      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    createCapsule();
    // 1 byte readable character ok.
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8("0"))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(nowTime)
        .setEndTime(nowTime + 24 * 3600 * 1000)
        .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum+1)));
      Assert.assertNotNull(assetIssueCapsule);
      tokenIdNum ++;

      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }
  }

  /*
   * Url length must between 1 to 256.
   */
  @Test
  public void urlTest() {
    long nowTime = new Date().getTime();

    //Empty url, throw exception
    Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(nowTime)
        .setEndTime(nowTime + 24 * 3600 * 1000)
        .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
        .setUrl(ByteString.EMPTY)
        .build());

    AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid url", e.getMessage());
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    String url256Bytes = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    //Too long url, throw exception. Max long is 256.
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(nowTime)
        .setEndTime(nowTime + 24 * 3600 * 1000)
        .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
        .setUrl(ByteString.copyFromUtf8(url256Bytes + "0"))
        .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid url", e.getMessage());
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum+1)));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    // 256 byte readable character just ok.
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(nowTime)
        .setEndTime(nowTime + 24 * 3600 * 1000)
        .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
        .setUrl(ByteString.copyFromUtf8(url256Bytes))
        .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum+1)));
      Assert.assertNotNull(assetIssueCapsule);
      tokenIdNum ++;
      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    createCapsule();
    // 1 byte url.
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(nowTime)
        .setEndTime(nowTime + 24 * 3600 * 1000)
        .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
        .setUrl(ByteString.copyFromUtf8("0"))
        .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum+1)));
      Assert.assertNotNull(assetIssueCapsule);
      tokenIdNum ++;
      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    createCapsule();
    // 1 byte space ok.
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(nowTime)
        .setEndTime(nowTime + 24 * 3600 * 1000)
        .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
        .setUrl(ByteString.copyFromUtf8(" "))
        .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum+1)));
      Assert.assertNotNull(assetIssueCapsule);
      tokenIdNum ++;

      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }
  }

  /*
   * Description length must less than 200.
   */
  @Test
  public void descriptionTest() {
    long nowTime = new Date().getTime();

    String description200Bytes = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef01234567";
    //Too long description, throw exception. Max long is 200.
    Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(nowTime)
        .setEndTime(nowTime + 24 * 3600 * 1000)
        .setDescription(ByteString.copyFromUtf8(description200Bytes + "0"))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());

    AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid description", e.getMessage());
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum+1)));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    // 200 bytes character just ok.
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(nowTime)
        .setEndTime(nowTime + 24 * 3600 * 1000)
        .setDescription(ByteString.copyFromUtf8(description200Bytes))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum+1)));
      tokenIdNum ++;
      Assert.assertNotNull(assetIssueCapsule);

      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    createCapsule();
    // Empty description is ok.
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(nowTime)
        .setEndTime(nowTime + 24 * 3600 * 1000)
        .setDescription(ByteString.EMPTY)
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum+1)));
      Assert.assertNotNull(assetIssueCapsule);
      tokenIdNum ++ ;

      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    createCapsule();
    // 1 byte space ok.
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(nowTime)
        .setEndTime(nowTime + 24 * 3600 * 1000)
        .setDescription(ByteString.copyFromUtf8(" "))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(tokenIdNum+1)));
      Assert.assertNotNull(assetIssueCapsule);
      tokenIdNum ++;

      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }
  }

  /*
   * Test FrozenSupply, 1. frozen_amount must greater than zero.
   */
  @Test
  public void frozenTest() {
    //frozen_amount = 0 throw exception.
    FrozenSupply frozenSupply = FrozenSupply.newBuilder().setFrozenDays(1).setFrozenAmount(0)
        .build();
    long nowTime = new Date().getTime();
    Any contract = Any.pack(
        Contract.AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
            .setNum(NUM)
            .setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .addFrozenSupply(frozenSupply)
            .build());

    AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Frozen supply must be greater than 0!", e.getMessage());
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    //frozen_amount < 0 throw exception.
    frozenSupply = FrozenSupply.newBuilder().setFrozenDays(1).setFrozenAmount(-1)
        .build();
    contract = Any.pack(
        Contract.AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
            .setNum(NUM)
            .setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .addFrozenSupply(frozenSupply)
            .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    ret = new TransactionResultCapsule();
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Frozen supply must be greater than 0!", e.getMessage());
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    long minFrozenSupplyTime = dbManager.getDynamicPropertiesStore().getMinFrozenSupplyTime();
    long maxFrozenSupplyTime = dbManager.getDynamicPropertiesStore().getMaxFrozenSupplyTime();

    //FrozenDays = 0 throw exception.
    frozenSupply = FrozenSupply.newBuilder().setFrozenDays(0).setFrozenAmount(1)
        .build();
    nowTime = new Date().getTime();
    contract = Any.pack(
        Contract.AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
            .setNum(NUM)
            .setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .addFrozenSupply(frozenSupply)
            .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    ret = new TransactionResultCapsule();
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals(
          "frozenDuration must be less than " + maxFrozenSupplyTime + " days " + "and more than "
              + minFrozenSupplyTime + " days", e.getMessage());
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    //FrozenDays < 0 throw exception.
    frozenSupply = FrozenSupply.newBuilder().setFrozenDays(-1).setFrozenAmount(1)
        .build();
    contract = Any.pack(
        Contract.AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
            .setNum(NUM)
            .setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .addFrozenSupply(frozenSupply)
            .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    ret = new TransactionResultCapsule();
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals(
          "frozenDuration must be less than " + maxFrozenSupplyTime + " days " + "and more than "
              + minFrozenSupplyTime + " days", e.getMessage());
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    //FrozenDays >  maxFrozenSupplyTime throw exception.
    frozenSupply = FrozenSupply.newBuilder().setFrozenDays(maxFrozenSupplyTime + 1)
        .setFrozenAmount(1)
        .build();
    contract = Any.pack(
        Contract.AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
            .setNum(NUM)
            .setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .addFrozenSupply(frozenSupply)
            .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    ret = new TransactionResultCapsule();
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals(
          "frozenDuration must be less than " + maxFrozenSupplyTime + " days " + "and more than "
              + minFrozenSupplyTime + " days", e.getMessage());
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    //frozen_amount = 1 and  frozenDays = 1 is OK
    frozenSupply = FrozenSupply.newBuilder().setFrozenDays(1).setFrozenAmount(1)
        .build();
    contract = Any.pack(
        Contract.AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
            .setNum(NUM)
            .setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .addFrozenSupply(frozenSupply)
            .build());

    actuator = new AssetIssueActuator(contract, dbManager);
    ret = new TransactionResultCapsule();
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }
  }

  /**
   * 1. start time should not be null 2. end time should not be null 3. start time >=
   * getHeadBlockTimeStamp 4. start time < end time
   */
  @Test
  public void issueTimeTest() {
    //empty start time will throw exception
    Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setEndTime(endTime)
        .setDescription(ByteString.copyFromUtf8("description"))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());
    AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Start time should be not empty", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    //empty end time will throw exception
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(startTime)
        .setDescription(ByteString.copyFromUtf8("description"))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());
    actuator = new AssetIssueActuator(contract, dbManager);
    ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("End time should be not empty", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    //startTime == now, throw exception
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(now)
        .setEndTime(endTime)
        .setDescription(ByteString.copyFromUtf8("description"))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());
    actuator = new AssetIssueActuator(contract, dbManager);
    ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Start time should be greater than HeadBlockTime", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    //startTime < now, throw exception
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(now - 1)
        .setEndTime(endTime)
        .setDescription(ByteString.copyFromUtf8("description"))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());
    actuator = new AssetIssueActuator(contract, dbManager);
    ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Start time should be greater than HeadBlockTime", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    //endTime == startTime, throw exception
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(startTime)
        .setEndTime(startTime)
        .setDescription(ByteString.copyFromUtf8("description"))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());
    actuator = new AssetIssueActuator(contract, dbManager);
    ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("End time should be greater than start time", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    //endTime < startTime, throw exception
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(endTime)
        .setEndTime(startTime)
        .setDescription(ByteString.copyFromUtf8("description"))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());
    actuator = new AssetIssueActuator(contract, dbManager);
    ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("End time should be greater than start time", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }

    //right issue, will not throw exception
    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setDescription(ByteString.copyFromUtf8("description"))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());
    actuator = new AssetIssueActuator(contract, dbManager);
    ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      AccountCapsule account = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      Assert.assertEquals(account.getAssetIssuedName().toStringUtf8(), NAME);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }
  }

  /**
   * an account should issue asset only once
   */
  @Test
  public void assetIssueNameTest() {
    Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setDescription(ByteString.copyFromUtf8("description"))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());
    AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(ASSET_NAME_SECOND))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setDescription(ByteString.copyFromUtf8("description"))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());
    actuator = new AssetIssueActuator(contract, dbManager);
    ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("An account can only issue one asset", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(ASSET_NAME_SECOND));
    }
  }

  @Test
  public void assetIssueTRXNameTest() {
//    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
    Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8("TRX"))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setDescription(ByteString.copyFromUtf8("description"))
        .setUrl(ByteString.copyFromUtf8(URL))
        .build());
    AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("assetName can't be trx", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(ASSET_NAME_SECOND));
    }
  }

  @Test
  public void frozenListSizeTest() {
    this.dbManager.getDynamicPropertiesStore().saveMaxFrozenSupplyNumber(3);
    List<FrozenSupply> frozenList = new ArrayList();
    for (int i = 0; i < this.dbManager.getDynamicPropertiesStore().getMaxFrozenSupplyNumber() + 2;
        i++) {
      frozenList.add(FrozenSupply.newBuilder()
          .setFrozenAmount(10)
          .setFrozenDays(3)
          .build());
    }

    Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setDescription(ByteString.copyFromUtf8("description"))
        .setUrl(ByteString.copyFromUtf8(URL))
        .addAllFrozenSupply(frozenList)
        .build());
    AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Frozen supply list length is too long", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }
  }

  @Test
  public void frozenSupplyMoreThanTotalSupplyTest() {
    this.dbManager.getDynamicPropertiesStore().saveMaxFrozenSupplyNumber(3);
    List<FrozenSupply> frozenList = new ArrayList();
    frozenList.add(FrozenSupply.newBuilder()
        .setFrozenAmount(TOTAL_SUPPLY + 1)
        .setFrozenDays(3)
        .build());
    Any contract = Any.pack(Contract.AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(NAME))
        .setTotalSupply(TOTAL_SUPPLY)
        .setTrxNum(TRX_NUM).setNum(NUM)
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setDescription(ByteString.copyFromUtf8("description"))
        .setUrl(ByteString.copyFromUtf8(URL))
        .addAllFrozenSupply(frozenList)
        .build());
    AssetIssueActuator actuator = new AssetIssueActuator(contract, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Frozen supply cannot exceed total supply", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }
  }


  /**
   * SameTokenName open, check invalid precision
   */
  @Test
  public void SameTokenNameCloseInvalidPrecision() {
//    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
    long nowTime = new Date().getTime();
    Any any = Any.pack(
        Contract.AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME))
            .setTotalSupply(TOTAL_SUPPLY)
            .setTrxNum(TRX_NUM)
            .setNum(NUM)
            .setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .setPrecision(7)
            .build());

    AssetIssueActuator actuator = new AssetIssueActuator(any, dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    byte[] stats = new byte[27];
    Arrays.fill(stats, (byte) 1);
    dbManager.getDynamicPropertiesStore()
        .statsByVersion(Parameter.ForkBlockVersionConsts.ENERGY_LIMIT, stats);
//    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("precision cannot exceed 6", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueV2Store().delete(ByteArray.fromString(NAME));
    }
  }


}