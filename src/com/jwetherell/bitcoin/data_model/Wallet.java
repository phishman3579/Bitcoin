package com.jwetherell.bitcoin.data_model;

public class Wallet {

    private final String    name;

    private long            pending     = 0;
    private long            balance     = 0;

    public Wallet(String name) {
        this.name = name;
    }

    public long getPending() {
        return pending;
    }

    public long getBalance() {
        return balance;
    }

    public Coin borrowCoin(String newOwner, int value) {
        pending += value;
        final String msg = "Borrowed "+value+" from "+name+"'s wallet={"+toString()+"}";
        System.out.println(msg);
        return (new Coin(name, newOwner, msg, value));
    }

    public void returnBorrowedCoin(Coin coin) {
        pending -= coin.value;
        coin.from = name;
        coin.to = name;
        final String msg = "Returning borrowed "+coin.value+" from "+name+"'s wallet={"+toString()+"}";
        System.out.println(msg);
    }

    public void removeBorrowedCoin(Coin coin) {
        pending -= coin.value;
        balance -= coin.value;
        final String msg = "Removed borrowed "+coin.value+" from "+name+"'s wallet={"+toString()+"}";
        System.out.println(msg);
    }

    public void addCoin(Coin coin) {
        balance += coin.value;
        coin.from = name;
        coin.to = name;
        System.out.println("Added "+coin.value+" to "+name+"'s wallet={"+toString()+"}");
    }

    public Coin removeCoin(String newOwner, int value) {
        balance -= value;
        final String msg = "Removed "+value+" from "+name+"'s wallet={"+toString()+"}";
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
        builder.append("balance=").append(balance);
        return builder.toString();
    }
}
