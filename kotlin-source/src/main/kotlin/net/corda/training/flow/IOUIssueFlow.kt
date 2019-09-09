package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.training.contract.IOUContract
import net.corda.training.state.IOUState

/**
 * This is the flow which handles issuance of new IOUs on the ledger.
 * Gathering the counterparty's signature is handled by the [CollectSignaturesFlow].
 * Notarisation (if required) and commitment to the ledger is handled by the [FinalityFlow].
 * The flow returns the [SignedTransaction] that was committed to the ledger.
 */
@InitiatingFlow
@StartableByRPC
class IOUIssueFlow(val state: IOUState) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val txBuilder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.single());
        val issueCommand = Command(IOUContract.Commands.Issue(), state.participants.map { it.owningKey });
        txBuilder.addCommand(issueCommand);
        txBuilder.addOutputState(state, IOUContract.IOU_CONTRACT_ID);

        // Verify transaction
        txBuilder.verify(serviceHub);

        // Get set of signers required from the participants who are not the node.
        // Signers except the flow initiator.
        val sessions = (state.participants - ourIdentity).map { initiateFlow(it)  }.toSet();

        val pendingTx = serviceHub.signInitialTransaction(txBuilder);

        // Start CollectSignaturesFlow subflow
        val signedTx = subFlow(CollectSignaturesFlow(pendingTx, sessions));

        return subFlow(FinalityFlow(signedTx, sessions));
    }
}

/**
 * This is the flow which signs IOU issuances.
 * The signing is handled by the [SignTransactionFlow].
 */
@InitiatedBy(IOUIssueFlow::class)
class IOUIssueFlowResponder(val flowSession: FlowSession): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an IOU transaction" using (output is IOUState)
            }
        }
        val signedtx = subFlow(signedTransactionFlow);

        return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = signedtx.id));
    }
}