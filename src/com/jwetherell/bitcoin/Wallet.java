package com.jwetherell.bitcoin;

import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

import com.jwetherell.bitcoin.common.Constants;
import com.jwetherell.bitcoin.common.KeyUtils;
import com.jwetherell.bitcoin.data_model.Block;
import com.jwetherell.bitcoin.data_model.Transaction;
import com.jwetherell.bitcoin.interfaces.TransactionListener;

/**
 * Class which handles the logic of maintaining the wallet including tracking serial numbers and public/private key encryption.
 * 
 * Thread-Safe (Hopefully)
 */
public class Wallet extends Peer {

    protected final KeyPairGenerator                        gen;
    protected final SecureRandom                            random;
    protected final Signature                               enc;
    protected final Signature                               dec;
    protected final KeyPair                                 pair;
    protected final PrivateKey                              privateKey;
    protected final KeyFactory                              keyFactory;
    protected final PublicKey                               publicKey;
    protected final byte[]                                  bPublicKey;
    { // initialize the private/public key associated with this wallet
        try {
            gen = KeyPairGenerator.getInstance("DSA", "SUN");
            random = SecureRandom.getInstance("SHA1PRNG", "SUN");
            gen.initialize(512, random);

            enc = Signature.getInstance("SHA1withDSA", "SUN");
            dec = Signature.getInstance("SHA1withDSA", "SUN");

            pair = gen.generateKeyPair();
            privateKey = pair.getPrivate();
            enc.initSign(privateKey);

            publicKey = pair.getPublic();
            bPublicKey = publicKey.getEncoded();

            keyFactory = KeyFactory.getInstance("DSA", "SUN");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Number of zeros in prefix of has to compute as the proof of work.
    private static final int                                NUMBER_OF_ZEROS                 = 2;
    // Number of transactions to aggregate in a Block
    private static final int                                NUMBER_OF_TRANSACTIONS_IN_BLOCK = 1;
    // Empty list
    private static final Transaction[]                      EMPTY                           = new Transaction[0];
    // Ranks Transactions by their value
    private static final Comparator<Transaction>            TRANSACTION_COMPARATOR          = new Comparator<Transaction>() {
        @Override
        public int compare(Transaction o1, Transaction o2) {
            // Higher value is processed quicker
            if (o1.value > o2.value)
                return -1;
            if (o2.value > o1.value)
                return 1;
            return 0;
        }
    };

    // Keep track of everyone's name -> public key
    private final Map<String,ByteBuffer>                    publicKeys                      = new ConcurrentHashMap<String,ByteBuffer>();
    // My BLockChain
    private final BlockChain                                blockChain;
    // Ranks transactions by their value to me 
    private final PriorityQueue<Transaction>                transactionQueue                = new PriorityQueue<Transaction>(10, TRANSACTION_COMPARATOR);

    public Wallet(String name) {
        super(name);
        // add the initial pub key
        this.publicKeys.put(BlockChain.NO_ONE, ByteBuffer.wrap(BlockChain.NO_ONE_PUB_KEY));
        // initialize the blockchain
        this.blockChain = new BlockChain(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected byte[] getPublicKey() {
        return bPublicKey;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlockChain getBlockChain() {
        return blockChain;
    }

    public long getBalance() {
        return blockChain.getBalance(myName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void newPublicKey(String name, byte[] publicKey) {
        publicKeys.put(name, ByteBuffer.wrap(publicKey));
    }

    /**
     * {@inheritDoc}
     * 
     * synchronized to protect enc from changing while processing
     */
    @Override
    protected synchronized byte[] signMsg(byte[] bytes) {
        return KeyUtils.signMsg(enc, bytes);
    }

    /**
     * {@inheritDoc}
     * 
     * synchronized to protect keyfactory/dec from changing while processing
     */
    @Override
    protected synchronized boolean verifyMsg(byte[] publicKey, byte[] signature, byte[] bytes) {
        return KeyUtils.verifyMsg(keyFactory, dec, publicKey, signature, bytes);
    }

    // synchronized to protect the blockchain from changing while processing
    public synchronized void sendCoin(TransactionListener listener, String name, int value) {
        // Iterate through the our unused transactions to see if we have enough coins
        final List<Transaction> inputList = new ArrayList<Transaction>();
        int coins = 0;
        for (Transaction t : this.blockChain.getUnused()) {
            if (!(t.to.equals(myName))) 
                continue;
            coins += t.value;
            inputList.add(t);
            if (coins >= value)
                break;
        }
        if (coins < value) {
            System.err.println(myName+" Sorry, you do not have enough coins.");
            return;
        }
        // Convert the list into an array
        final Transaction[] inputs = new Transaction[inputList.size()];
        for (int i=0; i<inputList.size(); i++)
            inputs[i] = inputList.get(i);

        // Since the entire input has to be used up, calculate if we will get any change left over
        // and send it back to myself.
        List<Transaction> outputList = new ArrayList<Transaction>();
        if (coins > value) {
            // I get some change back, so add myself as an output
            final int myCoins = coins - value;
            final String msg = "I get some change back.";
            final Transaction t = Transaction.newSignedTransaction(enc, myName, myName, msg, myCoins, inputs, EMPTY);
            outputList.add(t);
        }
        // Create the transaction for the recipient
        final String msg = "Here are some coins for you";
        final Transaction t = Transaction.newSignedTransaction(enc, myName, name, msg, value, inputs, EMPTY);
        outputList.add(t);
        // Convert the list into an array
        final Transaction[] outputs = new Transaction[outputList.size()];
        for (int i=0; i<outputList.size(); i++)
            outputs[i] = outputList.get(i);

        // Create the aggregate transaction, any value here will be a reward for the miner.
        final String myMsg = value+" from "+myName+" to "+name;
        final Transaction transaction = Transaction.newSignedTransaction(enc, myName, name, myMsg, 0, inputs, outputs);

        super.sendTransaction(name, transaction);
    }

    private Constants.Status checkSignature(String from, byte[] signature, byte[] bytes) {
        if (!publicKeys.containsKey(from))
            return Constants.Status.NO_PUBLIC_KEY;

        final byte[] key = publicKeys.get(from).array();
        if (!verifyMsg(key, signature, bytes)) {
            if (DEBUG)
                System.err.println(myName+" Bad signature on key from '"+from+"'");
            return Constants.Status.BAD_SIGNATURE;
        }

        return Constants.Status.SUCCESS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Constants.Status handleTransaction(String from, Transaction transaction, byte[] signature, byte[] bytes) {
        final Constants.Status status = checkSignature(from, signature, bytes);
        if (status != Constants.Status.SUCCESS) {
            if (DEBUG)
                System.err.println(myName+" handleTransaction() status="+status+"\n"+"transaction={\n"+transaction.toString()+"\n}");
            return status;
        }

        return Constants.Status.SUCCESS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Constants.Status handleTransactionAck(String from, Transaction transaction, byte[] signature, byte[] bytes) {
        final Constants.Status status = checkSignature(from, signature, bytes);
        if (status != Constants.Status.SUCCESS) {
            if (DEBUG)
                System.err.println(myName+" handleTransactionAck() status="+status+"\n"+"transaction={\n"+transaction.toString()+"\n}");
            return status;
        }

        return Constants.Status.SUCCESS;
    }

    /** Create a block given the these Transactions **/
    private Block getNextBlock(Transaction[] transactions) {
        Block trans = blockChain.getNextBlock(myName, transactions);
        // Need to be confirmed
        trans.confirmed = false;
        // Number of zeros in prefix of hash to compute
        trans.numberOfZeros = NUMBER_OF_ZEROS;
        return trans;
    }

    /**
     * {@inheritDoc}
     * 
     * synchronized to protected queue from changing while processing
     */
    @Override
    protected synchronized Block aggegateTransaction(Transaction transaction) {
        transactionQueue.add(transaction);
        if (transactionQueue.size() >= NUMBER_OF_TRANSACTIONS_IN_BLOCK) {
            Transaction[] transactions = new Transaction[NUMBER_OF_TRANSACTIONS_IN_BLOCK];
            for (int i=0; i<NUMBER_OF_TRANSACTIONS_IN_BLOCK; i++) {
                final Transaction t = transactionQueue.poll();
                transactions[i] = t;
            }
            final Block block = getNextBlock(transactions);
            return block;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Constants.Status checkTransaction(String from, Block block, byte[] signature, byte[] bytes) {
        final Constants.Status status = checkSignature(from, signature, bytes);
        if (status != Constants.Status.SUCCESS) {
            if (DEBUG)
                System.err.println(myName+" checkTransaction() status="+status+"\n"+"block={\n"+block.toString()+"\n}\n");
            return status;
        }

        return blockChain.checkHash(block);       
    }

    /**
     * {@inheritDoc}
     * 
     * synchronized to protect the blockchain from changing while processing
     */
    @Override
    protected synchronized Constants.Status handleConfirmation(String from, Block block, byte[] signature, byte[] bytes) {
        // Let's see if the nonce was computed correctly
        final boolean nonceComputedCorrectly = ProofOfWork.check(block.hash, block.nonce, block.numberOfZeros);
        if (!nonceComputedCorrectly) {
            if (DEBUG)
                System.err.println(myName+" Nonce was not computed correctly. block={\n"+block.toString()+"\n}");
            return Constants.Status.INCORRECT_NONCE;
        }

        // Check signature on the block
        final Constants.Status status = checkSignature(from, signature, bytes);
        if (status != Constants.Status.SUCCESS) {
            if (DEBUG)
                System.err.println(myName+" checkTransaction() status="+status+"\n"+"block={\n"+block.toString()+"\n}\n");
            return status;
        }

        for (Transaction trans : block.transactions) {
        // Check signature on the aggregate transaction and it's inputs/outputs
            { // Check aggregate transaction
                final String transactionFrom = trans.from;
                final byte[] transactionSignature = trans.signature.array();
                final byte[] transactionBytes = trans.header.getBytes();
                final Constants.Status transactionStatus = checkSignature(transactionFrom, transactionSignature, transactionBytes);
                if (transactionStatus != Constants.Status.SUCCESS) {
                    if (DEBUG)
                        System.err.println(myName+" checkTransaction() status="+transactionStatus+"\n"+"transaction={\n"+trans.toString()+"\n}\n");
                    return status;
                }
            }

            { // check signature on inputs
                for (Transaction i : trans.inputs) {
                    final String iFrom = i.from;
                    final byte[] iSignature = i.signature.array();
                    final byte[] iBytes = i.header.getBytes();
                    final Constants.Status iStatus = checkSignature(iFrom, iSignature, iBytes);
                    if (iStatus != Constants.Status.SUCCESS) {
                        if (DEBUG)
                            System.err.println(myName+" checkTransaction() status="+iStatus+"\n"+"transaction={\n"+i.toString()+"\n}\n");
                        return status;
                    }
                }
            }
            { // check signature on outputs
                for (Transaction o : trans.outputs) {
                    final String oFrom = o.from;
                    final byte[] oSignature = o.signature.array();
                    final byte[] oBytes = o.header.getBytes();
                    final Constants.Status oStatus = checkSignature(oFrom, oSignature, oBytes);
                    if (oStatus != Constants.Status.SUCCESS) {
                        if (DEBUG)
                            System.err.println(myName+" checkTransaction() status="+oStatus+"\n"+"transaction={\n"+o.toString()+"\n}\n");
                        return status;
                    }
                }
            }
        }

        // Everything looks good to me, try and add to blockchain
        return blockChain.addBlock(block);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected long mineHash(byte[] sha256, long numberOfZerosInPrefix) {
        return ProofOfWork.solve(sha256, numberOfZerosInPrefix);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(super.toString()).append(" blockChain={").append(blockChain.toString()).append("}");
        return builder.toString();
    }
}
