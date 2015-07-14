package com.jwetherell.bitcoin.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

//@formatter:off
@RunWith(Suite.class)
@SuiteClasses(
    {
        com.jwetherell.bitcoin.test.CoinTest.class,
        com.jwetherell.bitcoin.test.DataTest.class,
        com.jwetherell.bitcoin.test.WalletTest.class,
        com.jwetherell.bitcoin.test.UDPTest.class,
        com.jwetherell.bitcoin.test.TCPTest.class,
        com.jwetherell.bitcoin.test.MulticastTest.class,
        com.jwetherell.bitcoin.test.PeerTest.class,
    }
)
//@formatter:on

public class AllTest {
    // Ignore
}
