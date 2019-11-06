use rocket::{get, post};

#[post("/adopt")]
pub fn adopt() -> String {
    unimplemented!()
}

#[post("/create", data = "<post_data>")]
pub fn create(post_data: T) -> String {
    new::create();
}

#[get("/fetch")]
pub fn fetch() -> String {
    unimplemented!()
}

#[post("/new")]
pub fn new() -> String {
    unimplemented!()
}

#[post("/post")]
pub fn post() -> String {
    unimplemented!()
}

#[post("/stop")]
pub fn stop() -> String {
    unimplemented!()
}
