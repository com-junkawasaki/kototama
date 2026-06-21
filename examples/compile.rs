//! Compile a kami game `logic.clj` to wasm: `cargo run --example compile -- <in.clj> <out.wasm>`.
use std::{env, fs};
fn main() {
    let a: Vec<String> = env::args().collect();
    let (inp, outp) = (&a[1], &a[2]);
    let src = fs::read_to_string(inp).expect("read logic");
    let wasm = kototama::compile_game(&src).expect("compile_game");
    fs::write(outp, &wasm).expect("write wasm");
    eprintln!("compiled {} → {} ({} bytes)", inp, outp, wasm.len());
}
