# Identity & Auth

- Clerk is the only IdP.
- Convex stores `users` and `tokenIdentifier`.
- Clients call `users.upsertMe` after login.
- Device identity is explicit (`deviceId`) and used in Lamport ordering.
