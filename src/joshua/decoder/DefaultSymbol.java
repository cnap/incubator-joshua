/* This file is part of the Joshua Machine Translation System.
 * 
 * Joshua is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free
 * Software Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA 02111-1307 USA
 */
package joshua.decoder;

import joshua.corpus.AbstractSymbolTable;
import joshua.corpus.SymbolTable;
import joshua.util.io.LineReader;
import joshua.util.Regex;

import java.io.IOException;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

/**
 * this class implement 
 * (1) initialize the symbol table
 * (2) provide conversion between symbol and integers
 *
 * How to initialize the Symbol
 * Having multiple LM modes complicate the class, we have four LM modes: JAVA_LM, SRILM, Distributed_LM, and NONE_LM. The NONE_LM and JAVA_LM will be treated as same. 
 *JAVA_LM and NONE_LM: call add_global_symbols(true) to initialize
 *SRILM: the SRILM must first be initialized, then call add_global_symbols(false)
 *DistributedLM (from decoder): call init_sym_tbl_from_file(true)
 *DistributedLM (from LMServer): call init_sym_tbl_from_file(true/false)
 *
 * @author Zhifei Li, <zhifei.work@gmail.com>
 * @version $LastChangedDate$
 */
public abstract class DefaultSymbol
extends AbstractSymbolTable implements SymbolTable {
	
	public boolean is_reading_from_file = false;
	
	protected int lm_start_sym_id = 10000; // 1..10000 reserved for special purpose
	protected int lm_end_sym_id = 5000001; // max vocab 1000k
	
	// terminal symbol may get from a tbl file, srilm, or a lm file
	//**non-terminal symbol is always from myself, and the integer should always be negative	
	private HashMap<String,Integer> string2id = new HashMap<String,Integer>();
	private HashMap<Integer,String> id2string = new HashMap<Integer,String>();
	private int nonterminalCurrentId = -1;
	
	private static final Logger logger =
		Logger.getLogger(DefaultSymbol.class.getName());
	
	
	public DefaultSymbol() {
		// do nothing here, because we want the sub-class doing specific things
	}
	
	
	//protected abstract String getTerminalWord(int id);
	
	
	final public String getWord(int id) {
		if (isNonterminal(id)) {
			return getNonterminal(id);
		} else {
			return getTerminal(id);
		}
	}
	
	
	final public int getLowestID() {
		return this.lm_start_sym_id;
	}
	
	
	final public int getHighestID() {
		return this.lm_end_sym_id;
	}
	
	
	final public String getNonterminal(int id) {
		String res = this.id2string.get(id);
		if (null == res) {
			logger.severe("try to query the string for non exist id, must exit");
			System.exit(0);
		}
		return res;
	}
	
	
	final public String getWords(Integer[] ids) {
		StringBuffer sb = new StringBuffer();
		
		if (ids.length > 0) sb.append(this.getWord(ids[0]));
		for (int i = 1; i < ids.length; i++) {
			sb.append(" ").append(this.getWord(ids[i]));
		}
		return sb.toString();
	}
	
	final public String getWords(int[] ids) {
		StringBuffer sb = new StringBuffer();
		
		if (ids.length > 0) sb.append(this.getWord(ids[0]));
		for (int i = 1; i < ids.length; i++) {
			sb.append(" ").append(this.getWord(ids[i]));
		}
		return sb.toString();
	}
	
	final public String getWords(List<Integer> ids) {
		StringBuffer      sb = new StringBuffer();
		Iterator<Integer> it = ids.iterator();
		
		if (it.hasNext()) sb.append(this.getWord(it.next()));
		while (it.hasNext()) sb.append(" ").append(this.getWord(it.next()));
		return sb.toString();
	}
	
	
		
//	####### following functions used for TM only
	final public int addNonterminal(String str) {
		Integer res_id = this.string2id.get(str);
		if (null != res_id) { // already have this symbol
			if (! isNonterminal(res_id)) {
				logger.severe("Error, NONTSym: " + str + "; id: " + res_id);
				System.exit(1);
			}
			return res_id;
		} else {
			string2id.put(str, nonterminalCurrentId);
			id2string.put(nonterminalCurrentId, str);
			nonterminalCurrentId--;
			return (nonterminalCurrentId + 1);
		}
	}
	
	
	final public boolean isNonterminal(int id) {
		return (id < 0);
	}
	
	
	protected void initializeSymTblFromFile(String fname)
	throws IOException {
		is_reading_from_file = true;
		//### read file into tbls
		HashMap<String,Integer> tbl_str_2_id = new HashMap<String,Integer>();
		HashMap<Integer,String> tbl_id_2_str = new HashMap<Integer,String>();
		
		LineReader symboltableReader = new LineReader(fname);
		try { for (String line : symboltableReader) {
			String[] fds = Regex.spaces.split(line);
			if (2 != fds.length) {
				logger.warning("read index, bad line: " + line);
				continue;
			}
			String str = fds[0].trim();
			int id = Integer.parseInt(fds[1]);
			
			String uqniue_str;
			if (null != tbl_str_2_id.get(str)) { // it is quite possible that java will treat two stings as the same when other language (e.g., C or perl) treat them differently, due to unprintable symbols
				logger.warning("duplicate string (add fake): " + line);
				uqniue_str = str + id;//fake string
				//System.exit(1);//TODO
			} else {
				uqniue_str = str;
			}
			tbl_str_2_id.put(uqniue_str,id);
			
			//it is guaranteed that the strings in tbl_id_2_str are different
			if (null != tbl_id_2_str.get(id)) {
				logger.severe("Error: duplicate id, have to exit; " + line);
				System.exit(1);
			} else {
				tbl_id_2_str.put(id, uqniue_str);
			}
		} } finally { symboltableReader.close(); }
		
		/*if (tbl_id_2_str.size() >= lm_end_sym_id - lm_start_sym_id) {
			logger.severe("read symbol tbl, tlb is too big");
			System.exit(1);
		}*/
		
		//#### now add the tbl into srilm/java-tbl
		int n_added = 0;
		for (int i = lm_start_sym_id; i < lm_end_sym_id; i++) {
			// it is guranteed that the strings in tbl_id_2_str are different
			String str = tbl_id_2_str.get(i);
			int res_id;
			if (null != str) {
				res_id = this.addTerminal(str);
				n_added++;
			} else { // non-continous index
				logger.warning("added fake symbol, be alert");
				res_id = this.addTerminal("lzf" + i);
			}
			if (res_id != i) {
				logger.severe("id supposed: " + i + " != assigned " + res_id + " symbol:" + str);
				System.exit(1);
			}
			if (n_added >= tbl_id_2_str.size()) {
				break;
			}
		}
	}
	
	
	public int[] getIDs(String sentence) {
		return this.addTerminals(sentence);
	}

	public String getTerminals(int[] wordIDs) {
		return this.getWords(wordIDs);
	}

	public List<String> getWords() {
		return new ArrayList<String>(id2string.values());
	}

	public int size() {
		return this.id2string.size();
	}
}
