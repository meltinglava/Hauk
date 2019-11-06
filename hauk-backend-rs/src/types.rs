/*use rocket::data::{FromData, Outcome, Transform, Transformed};
use rocket::http::Status;
use rocket::{Data, Outcome::*, Request};

use std::io::{self, Read};
 */

use std::{
    collections::HashSet,
    convert::TryFrom,
    time::{Duration, SystemTime},
};

use rand::{distributions::Alphanumeric, prelude::*};
use uuid::Uuid;

pub struct SessionID {
    id: Uuid,
}

impl SessionID {
    pub fn new() -> Self {
        Self { id: Uuid::new_v4() }
    }
}

#[derive(Debug)]
struct Links {
    links: HashSet<String>,
}

fn gen_4_random_chars() -> Box<dyn Iterator<Item = char>> {
    Box::new(
        thread_rng()
            .sample_iter(&Alphanumeric)
            .map(|c| c.to_ascii_lowercase())
            .take(4),
    )
}

impl Links {
    fn new() -> Self {
        Self {
            links: HashSet::new(),
        }
    }

    fn new_link(&mut self) -> String {
        loop {
            let link: String = gen_4_random_chars()
                .chain(std::iter::once('-'))
                .chain(gen_4_random_chars())
                .collect();
            if !self.links.contains(&link) {
                self.links.insert(link.clone());
                return link;
            }
        }
    }
}

#[derive(Debug, Eq, PartialEq, Clone, Copy)]
enum Adoptable {
    Yes,
    No,
}

#[derive(Debug, Eq, PartialEq, Clone, Copy)]
enum ShareMode {
    CreateAlone,
    CreateGroup,
    JoinGroup,
}

#[derive(Debug)]
pub struct JoinCodes {
    used: HashSet<usize>,
}

impl JoinCodes {
    pub fn new() -> Self {
        JoinCodes {
            used: HashSet::new(),
        }
    }

    pub fn new_code(&mut self) -> usize {
        loop {
            let code: usize = thread_rng().gen_range(10usize.pow(5), 10usize.pow(6));
            if self.used.insert(code) {
                return code;
            }
        }
    }
}

impl TryFrom<usize> for ShareMode {
    type Error = &'static str;
    fn try_from(value: usize) -> Result<Self, Self::Error> {
        match value {
            0 => Ok(ShareMode::CreateAlone),
            1 => Ok(ShareMode::CreateGroup),
            2 => Ok(ShareMode::JoinGroup),
            _ => Err("Unknown ShareMode number"),
        }
    }
}

impl TryFrom<usize> for Adoptable {
    type Error = &'static str;
    fn try_from(value: usize) -> Result<Self, Self::Error> {
        match value {
            0 => Ok(Adoptable::No),
            1 => Ok(Adoptable::Yes),
            _ => Err("Unknown adoptable number"),
        }
    }
}

impl Default for Adoptable {
    fn default() -> Self {
        Self::No
    }
}
/*
impl<'a> FromData<'a> for Name<'a> {
    type Error = NameError;
    type Owned = String;
    type Borrowed = str;

    fn transform(_: &Request, data: Data) -> Transform<Outcome<Self::Owned, Self::Error>> {
        let mut stream = data.open().take(NAME_LIMIT);
        let mut string = String::with_capacity((NAME_LIMIT / 2) as usize);
        let outcome = match stream.read_to_string(&mut string) {
            Ok(_) => Success(string),
            Err(e) => Failure((Status::InternalServerError, NameError::Io(e))),
        };

        // Returning `Borrowed` here means we get `Borrowed` in `from_data`.
        Transform::Borrowed(outcome)
    }

    fn from_data(_: &Request, outcome: Transformed<'a, Self>) -> Outcome<Self, Self::Error> {
        // Retrieve a borrow to the now transformed `String` (an &str). This
        // is only correct because we know we _always_ return a `Borrowed` from
        // `transform` above.
        let string = outcome.borrowed()?;

        // Perform a crude, inefficient parse.
        let splits: Vec<&str> = string.split(" ").collect();
        if splits.len() != 2 || splits.iter().any(|s| s.is_empty()) {
            return Failure((Status::UnprocessableEntity, NameError::Parse));
        }

        // Return successfully.
        Success(Name {
            first: splits[0],
            last: splits[1],
        })
    }
}
*/

#[derive(Debug)]
pub struct Password {
    hashed: String,
}

#[derive(Debug)]
pub struct SessionCreationData {
    pwd: Password,
    start_time: SystemTime,
    dur: Duration,
    interval: Duration,
    ado: Adoptable,
    mode: ShareMode,
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::convert::TryInto;

    #[test]
    fn test_adoptable_default() {
        assert_eq!(Adoptable::No, Adoptable::default());
    }

    #[test]
    fn test_adpptable_froms() {
        assert_eq!(Ok(Adoptable::No), 0usize.try_into());
        assert_eq!(Ok(Adoptable::Yes), 1usize.try_into());
        assert_eq!(Err("Unknown adoptable number"), Adoptable::try_from(2));
    }

    #[test]
    fn test_share_mode_froms() {
        assert_eq!(Ok(ShareMode::CreateAlone), 0usize.try_into());
        assert_eq!(Ok(ShareMode::CreateGroup), 1usize.try_into());
        assert_eq!(Ok(ShareMode::JoinGroup), 2usize.try_into());
        assert_eq!(Err("Unknown ShareMode number"), ShareMode::try_from(3));
    }

    #[test]
    fn test_random_codes() {
        let mut codes = JoinCodes::new();
        let mut set = HashSet::new();
        assert!(std::iter::repeat_with(|| codes.new_code())
            .take(100)
            .all(|c| set.insert(c) && c >= 10usize.pow(5) && c <= 10usize.pow(6)))
    }

    #[test]
    fn test_random_links() {
        let mut links = Links::new();
        let mut set = HashSet::new();
        assert!(std::iter::repeat_with(|| links.new_link())
            .take(100)
            .all(|l| set.insert(l.clone())
                && l.chars().enumerate().all(|(n, c)| match n {
                    4 => c == '-',
                    0..9 => c.is_ascii_lowercase() || c.is_ascii_digit(),
                    _ => false,
                })));
    }
}
