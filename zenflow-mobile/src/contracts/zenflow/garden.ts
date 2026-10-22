/** Shared v1 wire contract. IDs are opaque strings; ownership never comes from payloads. */
export const VIEWPOINT_IDS = ['N00','N01','N02','N03','N04','N05','N06','N07','N08','N09','N10','N11','N12','N13'] as const;
export type ViewpointId = typeof VIEWPOINT_IDS[number];
export const ENVIRONMENT_PRESETS = ['day','dusk','night','rain','dawn','sunset'] as const;
export type EnvironmentPreset = typeof ENVIRONMENT_PRESETS[number];
export type GardenLevel = 'WORLD_OVERVIEW'|'SPACE_OVERVIEW'|'FIRST_PERSON';
export type GardenMode = 'LOADING'|'IDLE_LOOK'|'TRANSITION'|'CONTENT_PANEL'|'EDITING'|'DRAWING'|'RECORDING'|'PLACEMENT'|'POND_INTERACTION'|'SUSPENDED';
export type GardenPanel = 'places'|'wish'|'friends'|'mailbox'|'settings';
export type GardenEnvironment = {presetId: string; lampEnabled: boolean}; // Preserve future unknown values.
export type GardenFish = {instanceId:string; archetypeId:string; name:string};
export type GardenPlacement = {instanceId:string; archetypeId:string; anchorId:string; transform:{position:[number,number,number]; rotation:[number,number,number]; scale:[number,number,number]}};
export type GardenState = {gardenId:string;stateVersion:number;sceneVersion:string;fish:GardenFish[];placements:GardenPlacement[];environment:GardenEnvironment;updatedAt:string};
export type GardenOperation = {type:'INIT'; choices?:{environment?:GardenEnvironment}} | {type:'SET_FISH_NAME';instanceId:string;name:string}|{type:'SET_FISH_ARCHETYPE';instanceId:string;archetypeId:string}|{type:'UPSERT_PLACEMENT';placement:GardenPlacement}|{type:'REMOVE_PLACEMENT';instanceId:string}|{type:'SET_ENVIRONMENT';presetId?:EnvironmentPreset;lampEnabled?:boolean};
export type GardenPatch = {expectedVersion:number;clientRequestId:string;operations:GardenOperation[]};
export type MediaPurpose = 'PRIVATE_NOTE'|'PUBLIC_SUBMISSION'|'FRIEND_LETTER';
export type EntryKind = 'POST'|'WISH'|'REPLY'|'FOLLOWUP';
export type OperationReceipt = {clientRequestId:string;operationType:string;state:'PROCESSING'|'SUCCEEDED'|'REJECTED';resultType?:string;resultId?:string;resultVersion?:number;safeErrorCode?:string};
export type SceneEnvelope = {protocolVersion:1;requestId:string;sceneInstanceId:string;identityGeneration:number;type:string;payload:Record<string,unknown>};
export const GARDEN_LIMITS = {bridgeBytes:16384, audioBytes:5*1024*1024,audioSeconds:60,initialFish:3,maxFish:5} as const;
export const GARDEN_ANCHORS: Partial<Record<ViewpointId,readonly string[]>> = {N01:['pond.feed','pond.fish'],N03:['wish.take','wish.read','friends.open','mailbox.open']};

export type PublicProfile = {publicId:string;displayName:string;avatarMediaId:string|null};
export type FriendRequestState = 'PENDING'|'ACCEPTED'|'REJECTED'|'CANCELLED'|'REMOVED';
export type FriendDecision = {expectedVersion:number;clientRequestId:string;decision:'ACCEPT'|'REJECT'|'CANCEL'};
export type MediaState = 'RECEIVING'|'PROCESSING'|'READY'|'FAILED'|'EXPIRED';
export type MediaUpload = {uploadId:string;mediaId:string;version:number;expiresAt:string;purpose:MediaPurpose;allowedMimeTypes:string[];maxBytes:number;maxDurationSeconds:number|null};
export type CommunityEntryState = 'PENDING'|'APPROVED'|'REJECTED'|'WITHDRAWN'|'REMOVED';
export type CommunityEntry = {id:string;entryKind:EntryKind;rootPostId:string|null;replyToId:string|null;version:number;state:CommunityEntryState;text:string;mediaIds:string[];language:'zh-CN'|'en-US';anonymous:boolean;replyEnabled:boolean;approvedAt:string|null};
export type TreePage = {items:CommunityEntry[];nextCursor:string|null;hasMore:boolean};
export type LetterState = 'DELIVERED'|'WITHDRAWN';
export type Letter = {id:string;version:number;state:LetterState;sender:PublicProfile;recipient:PublicProfile;text:string;mediaIds:string[];deliveredAt:string};
export type SocialInboxItem = {id:string;eventKey:string;type:'WISH_REPLY'|'WISH_FOLLOWUP'|'FRIEND_REQUEST'|'FRIEND_ACCEPTED'|'LETTER';targetType:string;targetId:string;readAt:string|null;createdAt:string};
/** Removal keeps delivered history readable; block/withdrawal/account removal takes precedence. */
export const SOCIAL_LIFECYCLE = {removedFriendCanSend:false,removedFriendCanReadDeliveredHistory:true,blockedCanRead:false,unblockRestoresFriend:false,withdrawnCanRead:false} as const;
/** Existing bootstrap wire names remain authoritative; new capabilities default off until enabled. */
export type GardenCapabilities = {scene:boolean;chat:boolean;notes:boolean;creation:boolean;voiceMedia:boolean;social:boolean};
