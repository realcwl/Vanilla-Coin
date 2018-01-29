import java.util.ArrayList;

public class TxHandler {
    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all inputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        double totalInput = 0;
        double totalOutput = 0;

        for (int i=0; i<tx.numInputs(); i++) {
            Transaction.Input in = tx.getInput(i);
            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);

            /* (1) all inputs claimed by {@code tx} are in the current UTXO pool */
            if (!this.utxoPool.contains(utxo)) {
                return false;
            }

            /* (2) the signatures on each input of {@code tx} are valid */
            Transaction.Output output = utxoPool.getTxOutput(utxo);
            if (!Crypto.verifySignature(output.address, tx.getRawDataToSign(i), in.signature)) {
                return false;
            }
            totalInput += output.value;

            /* (3) no UTXO is claimed multiple times by {@code tx} */
            for (int j=i+1; j<tx.numInputs(); j++) {
                Transaction.Input otherIn = tx.getInput(j);
                UTXO otherUTXO = new UTXO(otherIn.prevTxHash, otherIn.outputIndex);
                if (utxo.equals(otherUTXO)) {
                    return false;
                }
            }
        }

        for (int i=0; i<tx.numOutputs(); i++) {
            /* (4) all of {@code tx}s output values are non-negative */
            double value = tx.getOutput(i).value;
            if (value < 0) {
                return false;
            }
            totalOutput += value;
        }

        /* (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output values */
        return totalInput >= totalOutput;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> txs = new ArrayList<>();
        for (Transaction tx: possibleTxs) {
            if(this.isValidTx(tx)) {
                /* add to accepted list */
                txs.add(tx);

                /* remove from pool */
                for (Transaction.Input input: tx.getInputs()) {
                    UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                    this.utxoPool.removeUTXO(utxo);
                }

                /* add to the pool */
                for (int i=0; i<tx.numOutputs(); i++) {
                    UTXO utxo = new UTXO(tx.getHash(), i);
                    this.utxoPool.addUTXO(utxo, tx.getOutput(i));
                }
            }
        }

        return txs.toArray(new Transaction[txs.size()]);
    }

}
