#![feature(exclusive_range_pattern, proc_macro_hygiene, decl_macro)]

//! Backend for Hauk, the open-source realtime location sharing project.
#[macro_use]
extern crate rocket;
mod types;

fn main() {
    /*    rocket::ignite()
       .mount(
           "/",
           routes![
               api::adopt,
               api::create,
               api::fetch,
               api::new,
               api::post,
               api::stop
           ],
       )
       .launch();
    */
    unimplemented!()
}
