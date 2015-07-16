package com.jwetherell.bitcoin.data_model;

import java.util.LinkedList;
import java.util.List;

/**
 * Wallet which tracks the balance and pending transactions.
 * 
 * Thread-Safe (Hopefully)
 */
public class Wallet {

    private final String        name;
    private final List<Coin>    transactions    = new LinkedList<Coin>();

    private volatile long       pending         = 0;
    private volatile long       balance         = 0;

    public Wallet(String name) {
        this.name = name;
    }

    public long getPending() {
        return pending;
    }

    public long getBalance() {
        return balance;
    }

    public synchronized Coin borrowCoin(String newOwner, int value) {
        pending += value;
        final String msg = "Borrowed "+value+" from "+name+"'s wallet\n"+toString();
        System.out.println(msg);
        return (new Coin(name, newOwner, msg, value));
    }

    public synchronized void returnBorrowedCoin(Coin coin) {
        pending -= coin.value;
        coin.to = name;
        final String msg = "Returning borrowed "+coin.value+" from "+name+"'s wallet\n"+toString();
        System.out.println(msg);
    }

    public synchronized void removeBorrowedCoin(Coin coin) {
        pending -= coin.value;
        balance -= coin.value;

        // Keep transaction history
        final Coin c = new Coin(coin);
        c.value *= -1; // since it's a remove operation
        transactions.add(c);

        final String msg = "Removed borrowed "+coin.value+" from "+name+"'s wallet\n"+toString();
        System.out.println(msg);
    }

    public synchronized void addCoin(Coin coin) {
        balance += coin.value;
        coin.to = name;

        // Keep transaction history
        final Coin c = new Coin(coin);
        transactions.add(c);

        System.out.println("Added "+coin.value+" to "+name+"'s wallet\n"+toString());
    }

    public synchronized Coin removeCoin(String newOwner, int value) {
        balance -= value;

        final String msg = "Removed "+value+" from "+name+"'s wallet\n"+toString();
        final Coin r = new Coin(name, newOwner, msg, value);

        // Keep transaction history
        final Coin c = new Coin(r);
        c.value *= -1; // since it's a remove operation
        transactions.add(c);

        System.out.println(msg);
        return (new Coin(name, newOwner, msg, value));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("name=").append(name).append(" ");
        builder.append("pending=").append(pending).append(" ");
        builder.append("balance=").append(balance).append("\n");

        builder.append("Transactions={").append("\n");
        for (Coin c : transactions) {
            if (c.value<0) 
                builder.append('\t').append(c.value*-1).append(" to ").append(c.to).append("\n");
            else
                builder.append('\t').append(c.value).append(" from ").append(c.from).append("\n");
        }
        builder.append("}");

        return builder.toString();
    }
}
