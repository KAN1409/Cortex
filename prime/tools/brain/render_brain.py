import numpy as np
from nilearn import datasets, surface
from scipy.ndimage import gaussian_filter
from PIL import Image
rng=np.random.default_rng(4)
fs=datasets.fetch_surf_fsaverage('fsaverage5')
V=[];Fc=[];S=[];off=0
for h in ('left','right'):
    c,f=surface.load_surf_mesh(fs['pial_'+h]); s=surface.load_surf_data(fs['sulc_'+h])
    V.append(c);Fc.append(f+off);S.append(s);off+=len(c)
V=np.vstack(V).astype(float);Fc=np.vstack(Fc);S=np.concatenate(S)
tri=V[Fc];fn=np.cross(tri[:,1]-tri[:,0],tri[:,2]-tri[:,0]);N=np.zeros_like(V)
for k in range(3): np.add.at(N,Fc[:,k],fn)
N/=np.linalg.norm(N,axis=1,keepdims=True)+1e-9
cen=tri.mean(1);cn=N[Fc].mean(1);cn/=np.linalg.norm(cn,axis=1,keepdims=True)+1e-9;cs=S[Fc].mean(1)
P=np.vstack([V,cen]);PN=np.vstack([N,cn]);PS=np.concatenate([S,cs])
sd=PS.std();gyr=np.clip(0.5-PS/(1.6*sd),0,1);edge=np.exp(-(PS/(0.22*sd))**2)
# --- synthetic cerebellum (two lobes, horizontal folia) + brainstem, placed below the occipital lobe
ymin=V[:,1].min();zmin=V[:,2].min()
n=9000;u=rng.normal(size=(n,3));u/=np.linalg.norm(u,axis=1,keepdims=True)
cx=np.sign(u[:,0])*(10+np.abs(u[:,0])*38);cy=ymin+30+u[:,1]*21;cz=zmin+24+u[:,2]*14
keep=~((np.abs(cx)<16)&(u[:,2]>0.3))   # vermis notch on top
CB=np.stack([cx,cy,cz],1)[keep];CBN=u[keep]
fol=np.exp(-((np.sin(u[keep][:,2]*24+u[keep][:,1]*3))/0.28)**2)
m=1600;t=rng.random(m);a=rng.random(m)*2*np.pi;r=8*(1-0.25*t)
ST=np.stack([r*np.cos(a),ymin+66-6*t+r*np.sin(a)*0.6,zmin+36-40*t],1)
STN=np.stack([np.cos(a),np.sin(a)*0.6,np.zeros(m)],1);STN/=np.linalg.norm(STN,axis=1,keepdims=True)
P=np.vstack([P,CB,ST]);PN=np.vstack([PN,CBN,STN])
gyr=np.concatenate([gyr,0.35*np.ones(len(CB)),0.3*np.ones(m)])
DW=np.concatenate([np.ones(len(PS)),0.42*np.ones(len(CB)),0.40*np.ones(m)])
edge=np.concatenate([edge,fol*0.8,0.15*np.ones(m)])
P-= (P.max(0)+P.min(0))/2
spark=rng.choice(len(V),900,replace=False);phase=rng.random(900)*2*np.pi;samp=0.6+0.8*rng.random(900)
W,H=720,600;scale=2.65
VIOLET=np.array([0.66,0.38,1.0]);BLUE=np.array([0.28,0.62,1.0]);PINK=np.array([1.0,0.62,0.98]);WHITE=np.array([0.92,0.95,1.0])
def proj(Q,QN,yaw,pitch=-14):
    t=np.radians(yaw);p=np.radians(pitch);x,y,z=Q[:,0],Q[:,1],Q[:,2]
    X=-y*np.cos(t)+x*np.sin(t);D=y*np.sin(t)+x*np.cos(t);Y=z*np.cos(p)-D*np.sin(p);D2=z*np.sin(p)+D*np.cos(p)
    if QN is None: return X,Y,D2,None
    nD=QN[:,1]*np.sin(t)+QN[:,0]*np.cos(t);nD2=QN[:,2]*np.sin(p)+nD*np.cos(p)
    nX=-QN[:,1]*np.cos(t)+QN[:,0]*np.sin(t);nY=QN[:,2]*np.cos(p)-nD*np.sin(p)
    return X,Y,D2,(nX,nY,nD2)
def splat(img,u,v,w,col):
    m=(u>=0)&(u<W-1)&(v>=0)&(v<H-1);u,v,w,col=u[m],v[m],w[m],col[m]
    iu=u.astype(int);iv=v.astype(int);fu=u-iu;fv=v-iv
    for du,dv,ww in ((0,0,(1-fu)*(1-fv)),(1,0,fu*(1-fv)),(0,1,(1-fu)*fv),(1,1,fu*fv)):
        for ch in range(3): np.add.at(img[:,:,ch],(iv+dv,iu+du),ww*w*col[:,ch])
def frame(yaw,fi):
    X,Y,D2,(nX,nY,nD2)=proj(P,PN,yaw)
    fres=(1-np.abs(nD2))**2.6
    sx=np.clip((X-X.min())/(np.ptp(X)+1e-9),0,1)[:,None]
    base=VIOLET*(1-sx)+BLUE*sx
    rimc=base*0.4+PINK*0.6*np.clip(0.5+0.5*(nY-0.2*nX),0,1)[:,None]+WHITE*0.15
    w_body=(0.07+0.22*gyr*(1-fres))*DW
    w_rim=1.05*fres*DW
    w_edge=0.85*edge*DW
    img=np.zeros((H,W,3))
    u=W/2+X*scale;v=H*0.47-Y*scale
    splat(img,u,v,w_body,base*0.8)
    splat(img,u,v,w_rim,rimc)
    splat(img,u,v,w_edge,(base*0.5+WHITE*0.6))
    img*=0.50
    glow=np.stack([gaussian_filter(img[:,:,c],9) for c in range(3)],2)
    core=np.stack([gaussian_filter(img[:,:,c],0.55) for c in range(3)],2)
    out=core+glow*2.0
    # sparkles riding on the surface, twinkling across frames
    sX,sY,sD,_=proj(V[spark]-((P.max(0)+P.min(0))/2*0),None,yaw)
    Vc=V-(np.vstack([V]).max(0)+np.vstack([V]).min(0))/2
    sX,sY,sD,_=proj(P[spark],None,yaw)
    tw=np.clip(np.sin(phase+fi*0.9)*0.5+0.5,0,1)**3*samp
    sp=np.zeros((H,W,3));splat(sp,W/2+sX*scale,H*0.47-sY*scale,tw*3.0,np.tile(WHITE,(len(tw),1)))
    sp=np.stack([gaussian_filter(sp[:,:,c],0.8) for c in range(3)],2)+np.stack([gaussian_filter(sp[:,:,c],3.5) for c in range(3)],2)*0.9
    out=out+sp*np.array([0.85,0.9,1.0])
    out=1-np.exp(-out*1.35)
    return (np.clip(out,0,1)*255).astype(np.uint8)
NF=24;yaws=np.linspace(-40,32,NF)
strip=np.zeros((H,W*NF,3),np.uint8)
for i,a in enumerate(yaws): strip[:,i*W:(i+1)*W]=frame(a,i)
Image.fromarray(strip).save('brain_glass_strip.jpg',quality=88,optimize=True,progressive=True)
Image.fromarray(strip[:,W*9:W*10]).save('brain_glass_f9.png')
print(strip.shape)
