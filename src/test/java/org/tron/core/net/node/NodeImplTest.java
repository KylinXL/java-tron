package org.tron.core.net.node;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.ConcurrentHashMap;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tron.common.overlay.client.PeerClient;
import org.tron.common.overlay.server.SyncPool;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.BlockCapsule.BlockId;
import org.tron.core.config.Parameter.NetConstants;
import org.tron.core.net.peer.PeerConnection;

public class NodeImplTest {

  private static NodeImpl p2pNode;

  @BeforeClass
  public static void init() {
    p2pNode = new NodeImpl();
  }

  @Test
  public void testDisconnectInactive() {
    // generate test data
    ConcurrentHashMap<Sha256Hash, Long> advObjWeRequested1 = new ConcurrentHashMap<>();
    ConcurrentHashMap<Sha256Hash, Long> advObjWeRequested2 = new ConcurrentHashMap<>();
    ConcurrentHashMap<Sha256Hash, Long> advObjWeRequested3 = new ConcurrentHashMap<>();
    ConcurrentHashMap<BlockId, Long> syncBlockRequested1 = new ConcurrentHashMap<>();
    ConcurrentHashMap<BlockId, Long> syncBlockRequested2 = new ConcurrentHashMap<>();
    ConcurrentHashMap<BlockId, Long> syncBlockRequested3 = new ConcurrentHashMap<>();

    advObjWeRequested1.put(new Sha256Hash(1, Sha256Hash.ZERO_HASH),
        System.currentTimeMillis() - NetConstants.ADV_TIME_OUT);
    syncBlockRequested1.put(new BlockId(),
        System.currentTimeMillis());
    advObjWeRequested2.put(new Sha256Hash(1, Sha256Hash.ZERO_HASH),
        System.currentTimeMillis());
    syncBlockRequested2.put(new BlockId(),
        System.currentTimeMillis() - NetConstants.SYNC_TIME_OUT);
    advObjWeRequested3.put(new Sha256Hash(1, Sha256Hash.ZERO_HASH),
        System.currentTimeMillis());
    syncBlockRequested3.put(new BlockId(),
        System.currentTimeMillis());

    PeerConnection peer1 = new PeerConnection();
    PeerConnection peer2 = new PeerConnection();
    PeerConnection peer3 = new PeerConnection();

    peer1.setAdvObjWeRequested(advObjWeRequested1);
    peer1.setSyncBlockRequested(syncBlockRequested1);
    peer2.setAdvObjWeRequested(advObjWeRequested2);
    peer2.setSyncBlockRequested(syncBlockRequested2);
    peer3.setAdvObjWeRequested(advObjWeRequested3);
    peer3.setSyncBlockRequested(syncBlockRequested3);

    // fetch failed
    SyncPool pool = new SyncPool(new PeerClient());
    pool.addActivePeers(peer1);
    p2pNode.setPool(pool);
    try {
      p2pNode.disconnectInactive();
      fail("disconnectInactive failed");
    } catch (RuntimeException e) {
      assertTrue("disconnect successfully, reason is fetch failed", true);
    }

    // sync failed
    pool = new SyncPool(new PeerClient());
    pool.addActivePeers(peer2);
    p2pNode.setPool(pool);
    try {
      p2pNode.disconnectInactive();
      fail("disconnectInactive failed");
    } catch (RuntimeException e) {
      assertTrue("disconnect successfully, reason is sync failed", true);
    }

    // should not disconnect
    pool = new SyncPool(new PeerClient());
    pool.addActivePeers(peer3);
    p2pNode.setPool(pool);
    try {
      p2pNode.disconnectInactive();
      assertTrue("not disconnect", true);
    } catch (RuntimeException e) {
      fail("should not disconnect!");
    }
  }
import com.google.protobuf.ByteString;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.tron.common.application.Application;
import org.tron.common.application.ApplicationFactory;
import org.tron.common.crypto.ECKey;
import org.tron.common.overlay.client.DatabaseGrpcClient;
import org.tron.common.utils.ByteArray;
import org.tron.core.Constant;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.utils.BlockUtil;
import org.tron.core.capsule.utils.TransactionUtil;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.net.message.BlockMessage;
import org.tron.core.net.peer.PeerConnection;
import org.tron.core.services.RpcApiService;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.BlockHeader;
import org.tron.protos.Protocol.DynamicProperties;
import org.tron.protos.Protocol.Transaction;

@Slf4j
public class NodeImplTest {

  private static AnnotationConfigApplicationContext context;

  private static Application appT;
  private static String dbPath = "output_nodeimpl_test";
  private static NodeImpl nodeImpl;
  private static Manager dbManager;
  private static NodeDelegateImpl nodeDelegate;

  static {
    Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
    context = new AnnotationConfigApplicationContext(DefaultConfig.class);
    Args.getInstance().setSolidityNode(true);
    appT = ApplicationFactory.create(context);
  }

  /**
   * init db.
   */
  @BeforeClass
  public static void init() {
    nodeImpl = context.getBean(NodeImpl.class);
    dbManager = context.getBean(Manager.class);
    nodeDelegate = new NodeDelegateImpl(dbManager);
    nodeImpl.setNodeDelegate(nodeDelegate);
  }

  /**
   * remo db when after test.
   */
  @AfterClass
  public static void removeDb() {
    Args.clearParam();

    File dbFolder = new File(dbPath);
    if (deleteFolder(dbFolder)) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
    context.destroy();
  }

  private static Boolean deleteFolder(File index) {
    if (!index.isDirectory() || index.listFiles().length <= 0) {
      return index.delete();
    }
    for (File file : index.listFiles()) {
      if (null != file && !deleteFolder(file)) {
        return false;
      }
    }
    return index.delete();
  }

  @Test
  public void testSyncBlockMessage() {
    PeerConnection peer = new PeerConnection();
    BlockCapsule genesisBlockCapsule = BlockUtil.newGenesisBlockCapsule();

    ByteString witnessAddress = ByteString.copyFrom(
        ECKey.fromPrivate(
            ByteArray.fromHexString(
                Args.getInstance().getLocalWitnesses().getPrivateKey()))
            .getAddress());
    BlockHeader.raw raw = BlockHeader.raw.newBuilder()
        .setTimestamp(System.currentTimeMillis())
        .setParentHash(genesisBlockCapsule.getParentHash().getByteString())
        .setNumber(genesisBlockCapsule.getNum() + 1)
        .setWitnessAddress(witnessAddress)
        .setWitnessId(1).build();
    BlockHeader blockHeader = BlockHeader.newBuilder()
        .setRawData(raw)
        .build();

    Block block = Block.newBuilder().setBlockHeader(blockHeader).build();

    BlockCapsule blockCapsule = new BlockCapsule(block);
    blockCapsule.sign(
        ByteArray.fromHexString(Args.getInstance().getLocalWitnesses().getPrivateKey()));
    blockCapsule.setMerkleRoot();
    BlockMessage blockMessage = new BlockMessage(blockCapsule);
    peer.getSyncBlockRequested().put(blockMessage.getBlockId(), System.currentTimeMillis());
    nodeImpl.onMessage(peer, blockMessage);
    Assert.assertEquals(peer.getSyncBlockRequested().size(), 0);
  }

  @Test
  public void testAdvBlockMessage() {
    PeerConnection peer = new PeerConnection();
    BlockCapsule genesisBlockCapsule = BlockUtil.newGenesisBlockCapsule();

    ByteString witnessAddress = ByteString.copyFrom(
        ECKey.fromPrivate(
            ByteArray.fromHexString(
                Args.getInstance().getLocalWitnesses().getPrivateKey()))
            .getAddress());
    BlockHeader.raw raw = BlockHeader.raw.newBuilder()
        .setTimestamp(System.currentTimeMillis())
        .setParentHash(genesisBlockCapsule.getParentHash().getByteString())
        .setNumber(genesisBlockCapsule.getNum() + 1)
        .setWitnessAddress(witnessAddress)
        .setWitnessId(1).build();
    BlockHeader blockHeader = BlockHeader.newBuilder()
        .setRawData(raw)
        .build();

    Block block = Block.newBuilder().setBlockHeader(blockHeader).build();

    BlockCapsule blockCapsule = new BlockCapsule(block);
    blockCapsule.sign(
        ByteArray.fromHexString(Args.getInstance().getLocalWitnesses().getPrivateKey()));
    blockCapsule.setMerkleRoot();
    BlockMessage blockMessage = new BlockMessage(blockCapsule);
    peer.getAdvObjWeRequested().put(blockMessage.getBlockId(), System.currentTimeMillis());
    nodeImpl.onMessage(peer, blockMessage);
    Assert.assertEquals(peer.getAdvObjWeRequested().size(), 0);
  }

}
