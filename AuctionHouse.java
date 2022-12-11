package lab9;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

public class AuctionHouse {
    static class NFT {
        public final int artistIdx;
        public final int price;

        public NFT(int artistIdx, int price) {
            this.artistIdx = artistIdx;
            this.price = price;
        }
    }

    static class AuctionBid {
        public int bidPrice;
        public String collectorName;

        public AuctionBid(int bidPrice, String collectorName) {
            this.bidPrice = bidPrice;
            this.collectorName = collectorName;
        }
    }

    static int failCount = 0;

    static final int MAX_NFT_PRICE = 100;
    static final int MAX_NFT_IDX = 100_000;
    static final int MAX_COLLECTOR_BID = MAX_NFT_IDX / 100;

    private static final int COLLECTOR_MIN_SLEEP = 10;
    private static final int COLLECTOR_MAX_SLEEP = 20;
    private static final int MAX_AUCTION_BIDS = 10;

    static final int ARTIST_COUNT = 10;
    static final int COLLECTOR_COUNT = 5;

    static final int INIT_ASSETS = MAX_NFT_IDX / 10 * MAX_NFT_PRICE;

    static int nftIdx = 0;
    static int remainingNftPrice = INIT_ASSETS;
    static NFT[] nfts = new NFT[MAX_NFT_IDX];

    static int totalCommission = 0;
    static int noAuctionAvailableCount = 0;
    static int soldItemCount = 0;

    // TODO for Task 2: data structure "auctionQueue"
    static BlockingQueue<AuctionBid> auctionQueue;
    // TODO for Task 3: data structure "owners"
    static ConcurrentHashMap<String, Integer> owners = new ConcurrentHashMap<>();
    static List<AtomicBoolean> notBidYet = new ArrayList<>(Collections.nCopies(COLLECTOR_COUNT, new AtomicBoolean(true)));

    public static void main(String[] args) throws InterruptedException {
        // Task 1
        List<Thread> artists = makeArtists();

        // Task 2
        Thread auctioneer = makeAuctioneer(artists);

        // Task 3
        List<Thread> collectors = makeCollectors(auctioneer);

        // TODO make sure that everybody starts working
        for(int i = 0; i < ARTIST_COUNT; i++){
            artists.get(i).start();
        }
        auctioneer.start();
        for(int i = 0; i < COLLECTOR_COUNT; i++){
            collectors.get(i).start();
        }
        // TODO make sure that everybody finishes working
        for(int i = 0; i < ARTIST_COUNT; i++){
            artists.get(i).join();
        }
        auctioneer.join();
        for(int i = 0; i < COLLECTOR_COUNT; i++){
            collectors.get(i).join();
        }
        runChecks();
        
    }

    // ------------------------------------------------------------------------
    // Task 1

    private static List<Thread> makeArtists() {
        // TODO create ARTIST_COUNT artists as threads, all of whom do the following, and return them as a list
        // every 20 milliseconds, try to create an NFT in the following way
            // the artist chooses a price for the nft between 100 and 1000
            // if the nfts array is already fully filled, the artist is done
            // if the price is more than remainingNftPrice, the artist is done
            // the artist creates the NFT in the next nfts array slot
            // ... and deduces the price from remainingNftPrice
        List<Thread> artists = new ArrayList<>();
        
        for(int i = 0; i < ARTIST_COUNT; i++){
            int I = i;
            artists.add(new Thread("artist" + I) {
                @Override
                public void run(){
                    while(true){
                        sleepForMsec(20);
                        //NFT newNFT = new NFT(I, getRandomBetween(100, 1000));
                        int price = getRandomBetween(100, 1000);
                        synchronized (nfts) {
                            if(nftIdx >= MAX_NFT_IDX || price > remainingNftPrice) break;
                            nfts[nftIdx++] = new NFT(I, price);
                            remainingNftPrice -= price;
                        }
                    }
                }
                    
            }
            );
        }
        
        return artists;
        
    }

    // ------------------------------------------------------------------------
    // Task 2


    private static Thread makeAuctioneer(List<Thread> artists) {
        // TODO create and return the auctioneer thread that does the following

        // run an auction if 1. any artists are still working 2. run 100 auctions after all artists are finished
            // otherwise, the auctioneer is done
        // a single auction is done like this:
            // pick a random NFT from nfts (keep in mind that nfts can still be empty)
            // create the auctionQueue
            // wait for auction bids
                // if there were already MAX_AUCTION_BIDS, the auction is done
                // if no bid is made within 1 millisecond, the auction is done
                // only for Task 3: if a bid is made and it has a better price than all previous ones, this is the currently winning bid
            // once the auction is done, add the commission to totalCommission
                // the commission is 10% of the price of the NFT (including the sum in the winning bid, if there was any)
                // only for Task 3: if there was a bid, increase soldItemCount and remember that the collector owns an NFT
            // now set auctionQueue to null and keep it like that for 3 milliseconds
            
            return new Thread("aunctionner"){
                @Override
                public void run(){
                    
                    while(true){
                        boolean artistWorks = false;
                        for(Thread artist : artists){
                            artistWorks |= artist.isAlive();
                        }
                        if(artistWorks) break;
                        try {
                            doAuction();
                            System.out.println("an auction is done");
                        } catch (Exception e) {
                            e.getMessage();
                        }
                    }
                    
                    for(int i = 0; i < 100; i++) {
                        try {
                            doAuction();
                        } catch (Exception e) {
                            e.getMessage();
                        }
                    }
                }
            
            };

            
    }

        

    // ------------------------------------------------------------------------
    // Task 3

    private static List<Thread> makeCollectors(Thread auctioneer) {
        // TODO create collectors now, the collectors' names are simply Collector1, Collector2, ...
        List<Thread> collectors = new ArrayList<>(COLLECTOR_COUNT);
        for(int i = 0; i < COLLECTOR_COUNT; i++){
            int I = i;
            collectors.add(new Thread("collector" + I){
                @Override
                public void run(){
                    while(true){
                        sleepForMsec(getRandomBetween(COLLECTOR_MIN_SLEEP, COLLECTOR_MAX_SLEEP));
                        
                        AuctionBid bid = new AuctionBid(getRandomBetween(1, MAX_COLLECTOR_BID), "Collector"+(I+1));
                        if(auctionQueue == null) noAuctionAvailableCount++;
                        else if((notBidYet.get(I)).getAndSet(false)){
                            try {
                                auctionQueue.put(bid);
                            } catch (Exception e) {
                                // TODO: handle exception
                            }
                        }
                        
                        if(!auctioneer.isAlive()) break;
                    }
                }
            });
        }
        
        return collectors;
        // work until the auctioneer is done (it is not isAlive() anymore)
            // sleep for COLLECTOR_MIN_SLEEP..COLLECTOR_MAX_SLEEP milliseconds randomly between each step
        // if there is no auction available, just increase noAuctionAvailableCount
        // if there is an ongoing auction, and you haven't bid on it already, place a bid
            // choose your bid between 1..MAX_COLLECTOR_BID randomly
    }

    // ------------------------------------------------------------------------
    // Tester

    private static String isOK(boolean condition) {
        if (!condition)   ++failCount;
        return isOkTxt(condition);
    }

    private static String isOkTxt(boolean condition) {
        return condition ? "GOOD" : "BAD ";
    }

    private static void runChecks() {
        if (Thread.activeCount() == 1) {
            System.out.printf("%s Only the current thread is running%n", isOK(true));
        } else {
            System.out.printf("%s %d threads are active, there should be only one%n", isOK(Thread.activeCount() == 1), Thread.activeCount());
        }

        System.out.printf("%s nftIdx > 0%n", isOK(nftIdx > 0));

        int soldPrice = IntStream.range(0, nftIdx).map(idx-> nfts[idx].price).sum();
        System.out.printf("%s Money is not lost: %d + %d = %d%n", isOK(soldPrice + remainingNftPrice == INIT_ASSETS), soldPrice, remainingNftPrice, INIT_ASSETS);

        System.out.printf("%s [Only Task 2] Total commission not zero: %d > 0%n", isOK(totalCommission > 0), totalCommission, INIT_ASSETS);

        System.out.printf("%s [Only Task 3] Sold item count not zero: %d > 0%n", isOK(soldItemCount > 0), soldItemCount, INIT_ASSETS);
        System.out.printf("%s [Only Task 3] Some collectors have become owners of NFTs: %d > 0%n", isOK(owners.size() > 0), owners.size(), INIT_ASSETS);
        System.out.printf("%s [Only Task 3] Sometimes, collectors found no auction: %d > 0%n", isOK(noAuctionAvailableCount > 0), noAuctionAvailableCount, INIT_ASSETS);

        System.out.printf("%s Altogether %d condition%s failed%n", isOkTxt(failCount == 0), failCount, failCount == 1 ? "" : "s");

        // forcibly shutting down the program (don't YOU ever do this)
        System.exit(42);
    }

    // ------------------------------------------------------------------------
    // Utilities

    private static int getRandomBetween(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max+1);
    }

    private static void sleepForMsec(int msec) {
        try {
            Thread.sleep(msec);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static void doAuction() throws InterruptedException{
         // pick a random NFT from nfts (keep in mind that nfts can still be empty)
            // create the auctionQueue
            // wait for auction bids
                // if there were already MAX_AUCTION_BIDS, the auction is done
                // if no bid is made within 1 millisecond, the auction is done
                // only for Task 3: if a bid is made and it has a better price than all previous ones, this is the currently winning bid
            // once the auction is done, add the commission to totalCommission
                // the commission is 10% of the price of the NFT (including the sum in the winning bid, if there was any)
                // only for Task 3: if there was a bid, increase soldItemCount and remember that the collector owns an NFT
            // now set auctionQueue to null and keep it like that for 3 milliseconds
        int ind;
        int count_bids = 0;
        AuctionBid winningBid = new AuctionBid(0, null);
        AuctionBid currentBid;
        synchronized(nfts){
            ind = getRandomBetween(0, nftIdx);
            auctionQueue = new ArrayBlockingQueue<AuctionBid>(MAX_AUCTION_BIDS);
            while(count_bids < MAX_AUCTION_BIDS && (currentBid = auctionQueue.poll(1, TimeUnit.MILLISECONDS)) != null) {
                if(currentBid.bidPrice > winningBid.bidPrice) winningBid = currentBid;
                ++count_bids;
            }
            NFT nft = nfts[ind];
            totalCommission += (nft.price + winningBid.bidPrice)/10;
            if(winningBid.bidPrice != 0) {
                soldItemCount++;
                // task3: store the owner of the nft
                owners.put(winningBid.collectorName, winningBid.bidPrice);
            }
            auctionQueue = null;
            for(AtomicBoolean nby : notBidYet) nby.set(true);
        }
        //NFT nft = nfts[getRandomBetween(0, nftIdx)];
    
        // notBidYet = new ArrayList<>(Collections.nCopies(COLLECTOR_COUNT, new AtomicBoolean(false)));
        sleepForMsec(3);
    }

    private static synchronized boolean isWorking(List<Thread> artists) {
        for(int i = 0; i < ARTIST_COUNT; i++){
            if(artists.get(i).isAlive()) return true;
        }
        return false;
    }
}
