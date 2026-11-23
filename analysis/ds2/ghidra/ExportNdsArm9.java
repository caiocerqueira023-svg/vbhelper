// Dumps decompiled pseudocode, function inventory and xrefs for a raw NDS ARM9 image.
// Args: <outputDir> [addr ...]
// Writes: functions.tsv, pseudocode/<addr>_<name>.c, xrefs.txt, references.txt
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionIterator;
import ghidra.program.model.symbol.Reference;
import ghidra.program.model.symbol.ReferenceIterator;
import ghidra.program.model.symbol.SourceType;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ExportNdsArm9 extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        File outDir = new File(args[0]);
        outDir.mkdirs();
        File pseudoDir = new File(outDir, "pseudocode");
        pseudoDir.mkdirs();

        DecompInterface decompiler = new DecompInterface();
        decompiler.openProgram(currentProgram);

        PrintWriter functions = new PrintWriter(new File(outDir, "functions.tsv"), "UTF-8");
        PrintWriter xrefs = new PrintWriter(new File(outDir, "xrefs.txt"), "UTF-8");
        int count = 0;
        FunctionIterator iterator = currentProgram.getFunctionManager().getFunctions(true);
        while (iterator.hasNext() && !monitor.isCancelled()) {
            Function function = iterator.next();
            Address entry = function.getEntryPoint();
            functions.println(entry + "\t" + function.getName() + "\t" + function.getBody().getNumAddresses()
                    + "\t" + function.getSignature());
            count++;
        }
        functions.close();

        // Named xref dump for explicitly requested addresses.
        for (int i = 1; i < args.length; i++) {
            Address target = toAddr(args[i]);
            xrefs.println("=== references to " + target + " ===");
            ReferenceIterator references = currentProgram.getReferenceManager().getReferencesTo(target);
            while (references.hasNext()) {
                Reference reference = references.next();
                Address from = reference.getFromAddress();
                Function containing = getFunctionContaining(from);
                xrefs.println("  from " + from + " type=" + reference.getReferenceType()
                        + " in " + (containing == null ? "<none>" : containing.getName() + "@" + containing.getEntryPoint()));
            }
        }
        xrefs.close();

        // Decompile every function reachable from the explicit addresses, plus all functions.
        PrintWriter pseudocode = new PrintWriter(new File(outDir, "all_pseudocode.c"), "UTF-8");
        iterator = currentProgram.getFunctionManager().getFunctions(true);
        int written = 0;
        while (iterator.hasNext() && !monitor.isCancelled()) {
            Function function = iterator.next();
            long size = function.getBody().getNumAddresses();
            if (size <= 0 || size > 4000) continue;
            DecompileResults results = decompiler.decompileFunction(function, 60, monitor);
            if (results == null || !results.decompileCompleted()) continue;
            pseudocode.println("// ---- " + function.getEntryPoint() + " " + function.getName()
                    + " size=" + size + " ----");
            pseudocode.println(results.getDecompiledFunction().getC());
            pseudocode.println();
            written++;
            if (written % 200 == 0) println("decompiled " + written);
        }
        pseudocode.close();
        decompiler.dispose();
        println("functions=" + count + " decompiled=" + written + " out=" + outDir);
    }
}
