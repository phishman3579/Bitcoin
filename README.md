# Bitcoin
An example Bitcoin implementation which can be used to learn about Bitcoin/Blockchain. This implementations is for educational use only.

# Overview.

## Wallet

The Wallet is how peers interact with the Bitcoin peer-to-peer network. The Wallet generates a public key and a private key which it uses to sign each Transaction. The pulic key is the send-to address used by the Bitcoin network. Each Wallet has the ability to send coins from your account to another account and it also has the ability to confirm Transactions (except it's own) which it receives from the Bitcoin peer-to-peer network.

```
    Wallet {
      sendCoin(entity, value); // Creates a new Transaction
      handleTransaction(Transaction); // Receives a unconfirmed Transaction
      handleConfirmation(Transaction); // Receives a confirmed Transaction and adds to blockchain
    }
```

## Transaction

Transactions are just a collection of input transactions, output transactions, a value, and a signature. 

```
    Transaction {
        byte[] Header;
        Transaction[] inputs;
        Transaction[] outputs;
        long value;
        byte[] signature;
    }
```

See the [Transaction Class](https://github.com/phishman3579/Bitcoin/blob/master/src/com/jwetherell/bitcoin/data_model/Transaction.java) for reference.

#### The Wallet also has a number of Transaction rules:

* Once a Transaction has been used as an input, it cannot be used again. 
* All inputs on a Transaction have to be completely consumed on a transaction.

Note: To send a Bitcoin transaction, you have to already own a Bitcoin. Getting an initial Bitcoin is usually done by trading something for a number of Bitcoins. One caveat of, having to own a Bitcoin to make a transaction, is the first transaction. The first transaction is called the genesis transaction, it is the only transaction which does not need input transactions.

### An example Transaction

If Justin wants to send 6 coins to George:

Ledger:

|  Justin's unused Transactions  |  George's unused Transaction  |
|  ----------------------------- | ----------------------------- | 
| Transaction #1 : 5 Coins       |                               |
| Transaction #2 : 3 Coins       |                               |
| Transaction #3 : 7 Coins       |                               |

```
    Aggregate Transaction #4 {
      byte[]        header      "6 coins for George and 2 coins to Justin"
      Transaction[] input       { Transaction #1, Transaction #2 }
      Transaction[] output      { Transaction #5, Transaction #6 }
      int           value       0
      byte[]        signature   "Justin's signature based on the Header"
    }
```
Note: The 'value' on the Aggregate Transaction (#4) is a reward for anyone who confirms the Transaction. The higher the reward, the better chance the Transaction will be processed quicker.

```
    Transaction #5 {
      byte[]        header      "2 coins to Justin"
      Transaction[] input       { Transaction #1, Transaction #2 }
      Transaction[] output      { }
      int           value       2
      byte[]        signature   "Justin's signature based on the Header"
    }

    Transaction #6 {
      byte[]        header      "6 coins for George"
      Transaction[] input       { Transaction #1, Transaction #2 }
      Transaction[] output      { }
      int           value       6
      byte[]        signature   "Justin's signature based on the Header"
    }
```

The Aggregate Transaction (#4) will remove Transaction #1 and #2 from Justin's unused Transactions. Since the total of all inputs is 8 coins, which is 2 more than what Justin wants to send to George, the output will contain a Transaction which sends 2 coins back to Justin.

The Wallet will use it's private key to sign the Header of the Aggregate Transactions (#4) and it will also sign each of the output Transactions (#5 & #6). It will then send Transaction #4 to the Bitcoin network for confirmation. 

Each peer on the Bitcoin network will receive the Transaction and try to confirm it. 

To confirm a Transaction, a Peer will:
* Check the Signature of Transaction against the public key of the sender. 

If it passes:
* Send the confirmed Transaction to the Bitcoin network.

## Block

The confirmed Transaction (#4) is added to a pool of confirmed Transactions. Peers (also called Miners) will gather confirmed Transactions from the pool and put them into a Block. A Block contains a number of confirmed Transactions, the Miner's signature, and a couple of other fields used for "Proof of work" processing.

```
    Block {
      Transaction[]     transactions
      int               nonce
      int               zeros
      byte[]            previousHash
      byte[]            nextHash
      byte[]            signature
    }
```

See the [Block Class](https://github.com/phishman3579/Bitcoin/blob/master/src/com/jwetherell/bitcoin/data_model/Block.java) for reference.

Miners will create a single 'block hash' from all the confirmed Transactions in the Block. They will then go through the process of "Proof of work". The goal of the "Proof of work" is to create a hash which begins with a random number of zeros (see the 'zeros' field). "Proof of work" is designed to be processor intensive which adds randomness to the time it takes to process a Block. A Miner will take the 'block hash' and append a random integer (called a 'nonce') to it. It will then create a new hash from 'block hash + nonce' and see if it satisfies the "Proof of work", this process will repeat until it finds a 'nonce' which satisfies the "Proof of work"

See the [Proof of work](https://github.com/phishman3579/Bitcoin/blob/master/src/com/jwetherell/bitcoin/ProofOfWork.java) for reference.

Once a Miner finds a 'nonce' which satisfies the "Proof of work", it will:
* Create another hash (see 'nextHash') using the Blockchain's current hash (see 'previousHash') and the 'block hash' 
* Send the Block to the Bitcoin network.

```
    Block #1 {
      Transaction[]     transactions    { Transaction #4 }
      int               nonce           453;
      int               zeros           3;
      byte[]            previousHash    "Blockchain hash #1";
      byte[]            nextHash        "Blockchain hash #2";
      byte[]            signature       "Miner's signature";
    }
```
Peers on the Bitcoin network will receive the Block and start confirming it. 

To confirm the Block, A Peer will:
* Make sure the 'nonce' satisfies the "Proof of work"
* Check the Block's signature 
* Check the signature of each Trasaction in the Block.

If everything passes:
* Add the block to it's Blockchain.
* Send the confirmed Block to the Bitcoin network

## Blockchain

The Blockchain is a simple structure which contains a list of confirmed Blocks, a list of Transactions in chronological order, a list of unused Transactions, and the current hash.

Note: all transactions in the same block are said to have happened at the same time.

```
    Blockchain {
        List<Block>         blockchain
        List<Transactions>  transactions
        List<Transaction>   unused
        byte[]              currentHash
    }
```

See the [Blockchain](https://github.com/phishman3579/Bitcoin/blob/master/src/com/jwetherell/bitcoin/BlockChain.java) for reference.


When the Peer adds the Block to the Blockchain, the Blockchain will:
* Check to see if the 'previousHash' from the Block matches it's 'currentHash', 
* Check to see if the input Transactions from all the Transactions in the Block are 'unused'

If everything passes:
* The Block is added to the 'blockChain'
* The Transaction is added to the 'transactions' list
* All 'input' transactions are removed from the 'unused' list
* All the 'output' transactions are added to the 'unused' list
* The 'currentHash' is updated to 'nextHash' from the current Block.

````
    Blockchain {
        List<Block>         blockchain      { Block #0 }
        List<Transactions>  transactions    { Transaction #0 }
        List<Transaction>   unused          { Transaction #1, Transaction #2, Transaction #3 }
        byte[]              currentHash     "Blockchain hash #1"
    }
```

Updated Blockchain.

````
    Blockchain {
        List<Block>         blockchain      { Block #0, Block #1 };
        List<Transactions>  transactions    { Transaction #0, Transaction #4 }
        List<Transaction>   unused          { Transaction #3, Transaction #5, Transaction #6 }
        byte[]              currentHash     "Blockchain hash #2"
    }
```

Ledger:

|  Justin's unused Transactions  |  George's unused Transaction  |
|  ----------------------------- | ----------------------------- | 
| Transaction #3 : 7 Coins       | Transaction #6 : 6 Coins      |
| Transaction #5 : 2 Coins       |                               |
|                                |                               |

Based off of:
http://www.michaelnielsen.org/ddi/how-the-bitcoin-protocol-actually-works/
http://www.imponderablethings.com/2013/07/how-bitcoin-works-under-hood.html

Also see the original paper:
https://bitcoin.org/bitcoin.pdf
