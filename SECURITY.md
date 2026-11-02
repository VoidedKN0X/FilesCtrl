# Security policy

Thank you for helping keep FilesCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/FilesCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of FilesCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

- Your passwords, SSH keys or files leaking to anyone other than your own NFS, SMB, SFTP, FTP or WebDAV server
- FilesCtrl connecting without checking a server's identity: an SFTP host key or a pinned certificate that changed and was accepted anyway, or HTTPS falling back to plain HTTP
- A file name from a server or an archive (zip, 7z, rar, tar) making FilesCtrl write outside the folder you chose
- Ways around the app lock, such as other apps reaching your servers through Android's file picker while FilesCtrl is locked, or protected screens showing up in screenshots when they shouldn't
- Another app on the phone being able to make FilesCtrl read, change or delete files you didn't ask for
- Anything that lets someone run code or read files they shouldn't

Problems in your NFS, SMB, SFTP, FTP or WebDAV server software itself should go to that project.

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
28:D8:D6:5B:3C:D6:BE:DB:A3:EB:E7:F8:52:21:C8:CE:AB:9B:07:24:02:43:86:35:76:4D:54:CD:E9:EA:70:07
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.
