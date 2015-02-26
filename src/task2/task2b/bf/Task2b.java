package task2.task2b.bf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;

import network.Server;
import task2.bloomFilter.BF;
import task2.task2b.PrepareData;
import task2.task2b.SNPEntry;
import util.EvaRunnable;
import util.GenRunnable;
import util.Utils;
import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;

public class Task2b {
	public static int NoM = 40;

	public static<T> T[] compute(CompEnv<T> env, T[] aliceBF, T[] bobBF) {
		IntegerLib<T> lib = new IntegerLib<>(env);
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
		T[] aliceBF, aliceBF2;
		T[] bobBF, bobBF2;
		T[] res, res2;
		BF bf;
		BF bf2;
		int totalSize = 0;

		@Override
		public void prepareInput(CompEnv<T> gen) throws Exception {
			CommandLine cmd = processArgs(args);
			if(cmd.hasOption("p"))
				NoM = Math.max(new Integer(cmd.getOptionValue("p")), NoM);
			HashSet<SNPEntry> data = PrepareData.readFile(cmd.getOptionValue("f"));

			int alicelength = 0;			for(SNPEntry e : data) alicelength +=e.value.length();

			gen.os.write(ByteBuffer.allocate(4).putInt(alicelength).array());
			gen.os.flush();

			int boblength = ByteBuffer.wrap(Server.readBytes(gen.is, 4)).getInt();
			totalSize = boblength+alicelength;
			bf = new BF(boblength+alicelength, NoM*(boblength+alicelength));
			bf2 = new BF(boblength+alicelength, NoM*(boblength+alicelength));
			bf2.sks = bf.sks;
			for(int i = 0; i < bf.k; ++i)
				gen.os.write(bf.sks[i]);
			gen.os.flush();

			for(SNPEntry e : data) {
				//for every pos: d+=max(d(x), d(y))
				for(int i = 0; i < e.value.length(); ++i){
					bf.insert(e.Pos(i));
				}
			}

			for(SNPEntry e : data) {
				// compute intersection which means: posx==posy & x==y
				for(int i = 0; i < e.value.length(); ++i)
					bf2.insert(e.PosVal(i));
			}

			aliceBF = gen.inputOfAlice(bf.bs);
			aliceBF2 = gen.inputOfAlice(bf2.bs);
			bobBF =  gen.inputOfBob(bf.bs);
			bobBF2 =  gen.inputOfBob(bf2.bs);

		}

		@Override
		public void secureCompute(CompEnv<T> gen) {
			res = compute(gen, aliceBF, bobBF);
			res2 = compute(gen, aliceBF2, bobBF2);

			IntegerLib<T> lib = new IntegerLib<T>(gen);
			T[] m_X = lib.sub(lib.toSignals(bf.m, res.length), res);
			T[] m_Y = lib.sub(lib.toSignals(bf.m, res2.length), res2);
			int length = 2*Math.max(m_X.length, m_Y.length)+2;
			res = lib.multiply(lib.padSignal(m_X, length), lib.padSignal(m_Y, length));
		}

		@Override
		public void prepareOutput(CompEnv<T> gen) {
			double tmp = Utils.toLong(gen.outputToAlice(res));
			int result = (int) (-1*bf.m/bf.k*Math.log(tmp/bf.m/bf.m) - totalSize);
			System.out.println( result );
		}		
	}

	public static class Evaluator<T> extends EvaRunnable<T> {
		T[] aliceBF;
		T[] bobBF;
		T[] aliceBF2;
		T[] bobBF2;
		T[] res, res2;
		BF bf;

		@Override
		public void prepareInput(CompEnv<T> gen) throws Exception {
			CommandLine cmd = processArgs(args);
			if(cmd.hasOption("p"))
				NoM = Math.max(new Integer(cmd.getOptionValue("p")), NoM);
			HashSet<SNPEntry> data = PrepareData.readFile(cmd.getOptionValue("f"));

			int boblength = 0;			for(SNPEntry e : data) boblength +=e.value.length();
			gen.os.write(ByteBuffer.allocate(4).putInt(boblength).array());
			gen.os.flush();

			int alicelength = ByteBuffer.wrap(Server.readBytes(gen.is, 4)).getInt();


			bf = new BF(boblength+alicelength, NoM*(boblength+alicelength));
			BF bf2 = new BF(boblength+alicelength, NoM*(boblength+alicelength));
			for(int i = 0; i < bf.k; ++i)
				try {
					bf.sks[i] = Server.readBytes(gen.is, bf.sks[i].length);
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			bf2.sks = bf.sks;


			for(SNPEntry e : data) {
				for(int i = 0; i < e.value.length(); ++i)
					bf.insert(e.Pos(i));
			}

			for(SNPEntry e : data) {
				for(int i = 0; i < e.value.length(); ++i)
					bf2.insert(e.PosVal(i));
			}

			aliceBF = gen.inputOfAlice(bf.bs);
			aliceBF2 = gen.inputOfAlice(bf2.bs);
			bobBF =  gen.inputOfBob(bf.bs);
			bobBF2 =  gen.inputOfBob(bf2.bs);
		}
		@Override
		public void secureCompute(CompEnv<T> gen) {
			res = compute(gen, aliceBF, bobBF);
			res2 = compute(gen, aliceBF2, bobBF2);

			IntegerLib<T> lib = new IntegerLib<T>(gen);
			T[] m_X = lib.sub(lib.toSignals(bf.m, res.length), res);
			T[] m_Y = lib.sub(lib.toSignals(bf.m, res2.length), res2);
			int length = 2*Math.max(m_X.length, m_Y.length)+2;
			res = lib.multiply(lib.padSignal(m_X, length), lib.padSignal(m_Y, length));
		}

		@Override
		public void prepareOutput(CompEnv<T> gen) {
			gen.outputToAlice(res);
		}
	}
}