# Bitcoin
An example Bitcoin implementation which can be used to help learn about Bitcoin/Blockchain. This implementations is for educational use only.

# General overview.

## Transactions
Transactions are just a collection of input transactions and output transactions, a value, and a signature. 

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

### Wallet

The Wallet is how you interact with the Bitcoin peer-to-peer network. The Wallet generates a public key and a private key which it uses to sign each Transaction. The pulic key is the send-to address used by the Bitcoin network. Each wallet has the ability to send coins from your account to another account and it also has the ability to confirm Transaction (expect it's own) which it receives from the Bitcoin peer-to-peer network.

```
    Wallet {
      sendCoin(entity, value); // Creates a new Transaction
      handleTransaction(Transaction); // Receives a unconfirmed Transaction
      handleConfirmation(Transaction); // Receives a confirmed Transaction and adds to blockchain
    }
```

The Wallet also implements a couple of rules.

#### Transaction rules

* Once a Transaction has been used as an input, it cannot be used again. 
* All inputs on a Transaction have to be completely consumed on a transaction.

Note: To send a Bitcoin transaction, you have to already own a Bitcoin. Getting an initial Bitcoin is usually done by trading a something for a number of Bitcoins. One caveat of, having to own a Bitcoin to make a transaction, is the first transaction. The first transaction is called the genesis transaction, it is the only transaction which does not need input transactions.

### An example Transaction

If Justin wants to send 6 coins to George:

|  Justin's unused Transactions  |  George's unused Transaction  |
|  ----------------------------- | ----------------------------- | 
| Transaction #1 : 5 Coins       |                               |
| Transaction #2 : 3 Coins       |                               |
| Transaction #3 : 6 Coins       |                               |

```
    Aggregate Transaction #4 {
      header = "6 coins for George and 2 coins to Justin"
      input[] = { Transaction #1, Transaction #2 }
      output[] = { Transaction #5, Transaction #6 }
      value = 0
      signature = Justin's signature based on the Header
    }
```
Note: The 'value' on the Aggregate Transaction (#4) is a reward for anyone who confirms the Transaction. The higher the reward, the better chance the Transaction will be processed quicker.

```
    Transaction #5 {
      header = "2 coins to Justin"
      input[] = { Transaction #1, Transaction #2 }
      output[] = { }
      value = 2
      signature = Justin's signature based on the Header
    }

    Transaction #6 {
      header = "6 coins for George"
      input[] = { Transaction #1, Transaction #2 }
      output[] = { }
      value = 6
      signature = Justin's signature based on the Header
    }
```

The Aggregate Transaction (#4) will consume Transaction #1 and #2 from Justin's unused Transactions. Since the total of all inputs is 8 coins which is 2 more than what Justin wants to send to George, the output will contain a Transaction which sends 2 coins back to Justin.

The Wallet will use it's private key to sign the Header of the Aggregate Transactions (#4) and it will also sign each of the output Transactions (#5 & #6).

Then it will send Transaction #4 to the Bitcoin network for confirmation. Each peer on the Bitcoin network will receive the Transaction and try to confirm it. To confirm a Transaction, a peer would check the Signature on the Header of the Transaction and see if it matches the public key of the sender. If the Signature matches, it'll send a confirmed Transaction to the Bitcoin network.

Peers (also called Miners) will gather confirmed Transactions on the Bitcoin network into Blocks. A Block contains a number of confirmation Transactions, a nonce, previous hash, next hash, and a signature.

```
    Block {
      Transaction[] transactions;
      byte[] signature;
      byte[] previousHash;
      byte[] nextHash;
    }
```

See the [Block Class](https://github.com/phishman3579/Bitcoin/blob/master/src/com/jwetherell/bitcoin/data_model/Block.java) for reference.

Based off of http://www.michaelnielsen.org/ddi/how-the-bitcoin-protocol-actually-works/
