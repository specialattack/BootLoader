package net.specialattack.loader;

public interface IClassTransformer {

    byte[] transform(String name, byte[] original);

}
