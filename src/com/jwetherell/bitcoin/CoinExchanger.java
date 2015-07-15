package com.jwetherell.bitcoin;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.jwetherell.bitcoin.data_model.Coin;
import com.jwetherell.bitcoin.data_model.Wallet;

/**
 * Class which handles the logic of maintaining the wallet.
 */
public class CoinExchanger extends Peer {

    // Tracking serial numbers of peers
    private final Map<String,Set<Long>>       recvSerials     = new ConcurrentHashMap<String,Set<Long>>();
    // My wallet
    private final Wallet                      wallet;

    public CoinExchanger(String name) {
        super(name);
        this.wallet = new Wallet(name);
    }

    public Wallet getWallet() {
        return wallet;
    }

    public void sendCoin(String name, int value) {
        // Borrow the coin from our wallet until we receive an ACK
        final Coin coin = wallet.borrowCoin(name,value);
        super.sendCoin(name,coin);
    }

    /** Really only here to open up the method for JUnits **/
    public void sendCoin(String name, Coin coin) {
        super.sendCoin(name,coin);
    }

    protected void handleCoin(String from, Coin coin) {
        // If not our coin, ignore
        if (!(name.equals(coin.to)))
            return;

        // Throw away duplicate coin requests
        Set<Long> set = recvSerials.get(from);
        if (set == null) {
            set = new HashSet<Long>();
            recvSerials.put(from, set);
        }
        final long serial = coin.getSerial();
        if (set.contains(serial)) {
            System.err.println("Not handling coin, it has a dup serial number. serial='"+coin.getSerial()+"' from='"+coin.from+"'");
            return;
        }

        // Yey, our coin!
        wallet.addCoin(coin);
        set.add(serial);
    }

    protected void handleCoinAck(Coin coin) {
        // The other peer ACK'd our transaction!
        wallet.removeBorrowedCoin(coin);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(super.toString()).append(" wallet={").append(wallet.toString()).append("}");
        return builder.toString();
    }
}
