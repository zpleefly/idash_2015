package task2.task2b.std;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;

import task2.circuit_from_compiler.Task2Automated;
import task2.obliviousmerge.ObliviousMergeLib;
import task2.task2b.PrepareData;
import task2.task2b.SNPEntry;
import util.EvaRunnable;
import util.GenRunnable;
import util.Utils;
import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;

public class Task2b {
	public static<T> T[] compute(CompEnv<T> env, T[][] scData) {
		ObliviousMergeLib<T> lib = new ObliviousMergeLib<T>(env);
		lib.bitonicMerge(scData, lib.SIGNAL_ZERO);
		T[] resBit = lib.zeros(scData.length);
		for(int i = 0; i < scData.length-1; ++i) {			
			T eq = lib.eq(scData[i], scData[i+1]);
			resBit[i] = lib.not(eq);
		}
		T[] res = lib.numberOfOnes(resBit);
		res = lib.add(res, lib.toSignals(1, res.length));
		return res;
	}

	public static<T> T[] computeAuto(CompEnv<T> env, T[][] scData) {
		Task2Automated<T> a;
		T[] ret = null;
		try {
			a = new Task2Automated<T>(env, scData[0].length, (int) Math.ceil(Math.log(scData.length)/Math.log(2)) );
			ret = a.funct(scData, scData.length);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ret;
	}

	static CommandLine processArgs(String[] args) throws Exception {
		Options options = new Options();
		options.addOption("a", false, "automated");
		options.addOption("f", "file", true, "file");
		options.addOption("p", "precision", true, "precision");


		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, args);

		if(!cmd.hasOption("f")) {
			throw new Exception("argument format is wrong!");
		}
		if(cmd.hasOption("a"))
			System.out.println("Running the program with automatically generated circuits");
		else
			System.out.println("Running the program with manually generated circuits");
		return cmd;
	}


	public static class Generator<T> extends GenRunnable<T> {
		BigInteger[] in, in2;
		T[] res, res2;
		int totalSize;
		int LEN;
		boolean automated;
		int alicelength; int boblength;
		@Override
		public void prepareInput(CompEnv<T> gen) throws Exception {
			CommandLine cmd = processArgs(args);
			automated = cmd.hasOption("a");
			HashSet<SNPEntry> data = PrepareData.readFile(cmd.getOptionValue("f"));

			for(SNPEntry e : data) alicelength +=e.value.length();

			byte[] seed = new byte[10];CompEnv.rnd.nextBytes(seed);
			gen.channel.writeInt(alicelength);
			gen.channel.writeByte(seed, seed.length);
			gen.channel.flush();
			boblength = gen.channel.readInt();
			totalSize = boblength+alicelength;
			LEN = (int) Math.ceil((Math.log(totalSize)/Math.log(2)*2+10));
			if(cmd.hasOption("p"))
				LEN = (int) Math.ceil((Math.log(totalSize)/Math.log(2)*2+new Integer(cmd.getOptionValue("p"))));

			in = new BigInteger[alicelength];
			in2 = new BigInteger[alicelength];
			int cnt = 0;
			for(SNPEntry e : data) {
				for(int i = 0; i < e.value.length(); ++i){
					in[cnt] = SNPEntry.HashToBI(e.Pos(i), LEN, seed);
					cnt++;
				}
			}
			cnt = 0;
			for(SNPEntry e : data) {
				for(int i = 0; i < e.value.length(); ++i){
					in2[cnt] = SNPEntry.HashToBI(e.PosVal(i), LEN, seed);
					cnt++;
				}
			}

			Arrays.sort(in);
			Arrays.sort(in2);
		}

		@Override
		public void secureCompute(CompEnv<T> gen) {
			boolean[][] clear = new boolean[alicelength][];
			for(int i = 0; i < in.length;  ++i)
				clear[i] = Utils.fromBigInteger(in[i], LEN);

			T[][] Alice = gen.inputOfAlice(clear);
			T[][] Bob = gen.inputOfBob(new boolean[boblength][LEN]);

			T[][] scData = gen.newTArray(totalSize, LEN);

			System.arraycopy(Alice, 0, scData, 0, Alice.length);
			System.arraycopy(Bob, 0, scData, Alice.length, Bob.length);
			res = compute(gen, scData);


			clear = new boolean[alicelength][];
			for(int i = 0; i < in.length;  ++i)
				clear[i] = Utils.fromBigInteger(in2[i], LEN);

			Alice = gen.inputOfAlice(clear);
			Bob = gen.inputOfBob(new boolean[boblength][LEN]);

			scData = gen.newTArray(totalSize, LEN);

			System.arraycopy(Alice, 0, scData, 0, Alice.length);
			System.arraycopy(Bob, 0, scData, Alice.length, Bob.length);
			res2 = compute(gen, scData);

			IntegerLib<T> lib = new IntegerLib<T>(gen);
			res = lib.add(lib.padSignal(res, 32), lib.padSignal(res2, 32));
		}

		@Override
		public void prepareOutput(CompEnv<T> gen) {
			double tmp = Utils.toLong(gen.outputToAlice(res));
			int result = (int)(tmp - totalSize);
			System.out.println("Edit Distance: "+result );
		}		
	}

	public static class Evaluator<T> extends EvaRunnable<T> {
		T[] res, res2;
		int totalSize;
		boolean automated;
		BigInteger[] in, in2;
		int LEN;
		int alicelength;
		int boblength = 0;			
		@Override
		public void prepareInput(CompEnv<T> gen) throws Exception {
			CommandLine cmd = processArgs(args);
			automated = cmd.hasOption("a");
			HashSet<SNPEntry> data = PrepareData.readFile(cmd.getOptionValue("f"));
			LEN = (int) Math.ceil((Math.log(totalSize)/Math.log(2)*2+10));
			if(cmd.hasOption("p"))
				LEN = (int) Math.ceil((Math.log(totalSize)/Math.log(2)*2+new Integer(cmd.getOptionValue("p"))));

			for(SNPEntry e : data) boblength +=e.value.length();
			gen.channel.writeInt(boblength);
			gen.channel.flush();

			alicelength = gen.channel.readInt();
			byte[] seed = gen.channel.readBytes(10);
			totalSize  = alicelength+boblength;

			in = new BigInteger[boblength];
			in2 = new BigInteger[boblength];
			int cnt = 0;
			for(SNPEntry e : data) {
				for(int i = 0; i < e.value.length(); ++i){
					in[cnt] = SNPEntry.HashToBI(e.Pos(i), LEN, seed).negate();
					cnt++;
				}
			}
			cnt = 0;
			for(SNPEntry e : data) {
				for(int i = 0; i < e.value.length(); ++i){
					in2[cnt] = SNPEntry.HashToBI(e.PosVal(i), LEN, seed).negate();
					cnt++;
				}
			}

			Arrays.sort(in);
			Arrays.sort(in2);


		}
		@Override
		public void secureCompute(CompEnv<T> gen) {
			boolean[][] clear = new boolean[boblength][];
			for(int i = 0; i < in.length;  ++i)
				clear[i] = Utils.fromBigInteger(in[i].negate(), LEN);

			T[][] Alice = gen.inputOfAlice(new boolean[alicelength][LEN]);
			T[][] Bob = gen.inputOfBob(clear);

			T[][] scData = gen.newTArray(totalSize, LEN);
			System.arraycopy(Alice, 0, scData, 0, Alice.length);
			System.arraycopy(Bob, 0, scData, Alice.length, Bob.length);
			res = compute(gen, scData);

			clear = new boolean[boblength][];
			for(int i = 0; i < in.length;  ++i)
				clear[i] = Utils.fromBigInteger(in2[i].negate(), LEN);

			Alice = gen.inputOfAlice(new boolean[alicelength][LEN]);
			Bob = gen.inputOfBob(clear);
			scData = gen.newTArray(totalSize, LEN);
			System.arraycopy(Alice, 0, scData, 0, Alice.length);
			System.arraycopy(Bob, 0, scData, Alice.length, Bob.length);
			res2 = compute(gen, scData);

			IntegerLib<T> lib = new IntegerLib<T>(gen);
			res = lib.add(lib.padSignal(res, 32), lib.padSignal(res2, 32));
		}

		@Override
		public void prepareOutput(CompEnv<T> gen) {
			gen.outputToAlice(res);
		}
	}
}