import argparse
import secrets
import string


DEFAULT_LENGTH = 48
DEFAULT_PREFIX = "mca_"
ALPHABET = string.ascii_letters + string.digits


def generate_secret(length: int, prefix: str) -> str:
    if length < 32:
        raise ValueError("length must be at least 32")

    body = "".join(secrets.choice(ALPHABET) for _ in range(length))
    return f"{prefix}{body}"


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate a sharedSecret for McStreamApi Plugin and AuthServer."
    )
    parser.add_argument(
        "--length",
        type=int,
        default=DEFAULT_LENGTH,
        help=f"Secret body length. Minimum: 32. Default: {DEFAULT_LENGTH}.",
    )
    parser.add_argument(
        "--prefix",
        default=DEFAULT_PREFIX,
        help=f"Secret prefix. Default: {DEFAULT_PREFIX}",
    )
    args = parser.parse_args()

    print(generate_secret(args.length, args.prefix))


if __name__ == "__main__":
    main()
