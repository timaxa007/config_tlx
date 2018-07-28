package timaxa007.config_tlx;

public interface IProcessConfig<T extends Object> {

	T newInstance(String arg1);
	void process(final String arg1, final String arg2, T t);
	void finish(T t, String id);

}
