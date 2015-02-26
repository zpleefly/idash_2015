package task2.task2a.bf;

import java.nio.ByteBuffer;
import java.util.HashSet;

import network.Server;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;

import task2.bloomFilter.BF;
import task2.task2a.PrepareData;
import task2.task2a.SNPEntry;
import util.EvaRunnable;
import util.GenRunnable;
import util.Utils;
import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;

public class Task2a {
	public static int NoM = 20;

	public static<T> T[] compute(CompEnv<T> env, T[] aliceBF, T[] bobBF) {
		IntegerLib<T> lib = new IntegerLib<>(env);
		System.out.println("Bloom Filter Size: "+aliceBF.length);
		T[] aUb = lib.or(aliceBF, bobBF);
		return lib.numberOfOnes(aUb);
	}

	static CommandLine processArgs(String[] args) throws Exception {
		Options options = new Options();
		options.addOption("f", "file", true, "file");
		options.addOption("p", "filprecisione", true, "precision");

		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, args);

		if(!cmd.hasOption("f")) {
			throw new Exception("wrong input");
		}
		return cmd;
	}

	public static class Generator<T> extends GenRunnable<T> {
		T[] aliceBF;
		T[] bobBF;
		T[] res;
		BF bf;
		int totalSize;

		@Override
		public void prepareInput(CompEnv<T> gen) throws Exception {
			CommandLine cmd = processArgs(args);
			if(cmd.hasOption("p"))
				NoM = new Integer(cmd.getOptionValue("p"));
			HashSet<SNPEntry> data = PrepareData.readFile(cmd.getOptionValue("f"));

			int alicelength = data.size();
			gen.os.write(ByteBuffer.allocate(4).putInt(data.size()).array());
			gen.os.flush();
			int boblength = ByteBuffer.wrap(Server.readBytes(gen.is, 4)).getInt();
			totalSize = boblength+alicelength;
			
			bf = new BF(boblength+alicelength, NoM*(boblength+alicelength));
			for(int i = 0; i < bf.k; ++i)
				gen.os.write(bf.sks[i]);
			gen.os.flush();

			double t1 = System.nanoTime();
			for(SNPEntry e : data)
				bf.insert(e.toString());
			double t2 = System.nanoTime();
			System.out.println(t2-t1);
			aliceBF = gen.inputOfAlice(bf.bs);
			bobBF =  gen.inputOfBob(bf.bs);
		}

		@Override
		public void secureCompute(CompEnv<T> gen) {
			res = compute(gen, aliceBF, bobBF);
		}

		@Override
		public void prepareOutput(CompEnv<T> gen) {
			int r = Utils.toInt(gen.outputToAlice(res));
			r = bf.countToSize(r);
			System.out.println(2*r-totalSize);
		}		
	}

	public static class Evaluator<T> extends EvaRunnable<T> {
		T[] aliceBF;
		T[] bobBF;
		T[] res;

		@Override
		public void prepareInput(CompEnv<T> gen) throws Exception {
			CommandLine cmd = processArgs(args);
			if(cmd.hasOption("p"))
				NoM = new Integer(cmd.getOptionValue("p"));
			HashSet<SNPEntry> data = PrepareData.readFile(cmd.getOptionValue("f"));
			int boblength = data.size();

			gen.os.write(ByteBuffer.allocate(4).putInt(data.size()).array());
			gen.os.flush();
			int alicelength = ByteBuffer.wrap(Server.readBytes(gen.is, 4)).getInt();

			BF bf = new BF(boblength+alicelength, NoM*(boblength+alicelength));
			for(int i = 0; i < bf.k; ++i)
				bf.sks[i] = Server.readBytes(gen.is, bf.sks[i].length);

			for(SNPEntry e : data)
				bf.insert(e.toString());

			aliceBF = gen.inputOfAlice(bf.bs);
			bobBF =  gen.inputOfBob(bf.bs);
		}

		@Override
		public void secureCompute(CompEnv<T> gen) {
			res = compute(gen, aliceBF, bobBF);
		}

		@Override
		public void prepareOutput(CompEnv<T> gen) {
			gen.outputToAlice(res);
		}
	}
}