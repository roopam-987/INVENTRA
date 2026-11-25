#include <iostream>
#include <vector>
#include <string>
#include <stack>
#include <queue>
#include <algorithm>
#include <iomanip>
using namespace std;

// INVENTRA - DATA STRUCTURES MODULE
// [CO1] Linear data structures
// [CO2] Arrays and 2D arrays
// [CO3] Singly and doubly linked lists
// [CO4] Stack and circular queue
// [CO5] Searching and sorting
// [CO6] Hashing with separate chaining

const int ROWS=3, COLS=4, HASH_SIZE=13;

struct Asset {
    string id;
    string status;
    string issue;
    int severity;
};

// [CO3] Singly linked list: complaint history
struct SNode {
    string complaint;
    SNode* next;
    SNode(string c):complaint(c),next(nullptr){}
};

class ComplaintList {
    SNode* head=nullptr;
public:
    void add(string c){
        SNode* n=new SNode(c);
        n->next=head;
        head=n;
    }

    void display(){
        cout<<"\n--- COMPLAINT HISTORY ---\n";
        for(SNode* p=head;p;p=p->next)
            cout<<" -> "<<p->complaint<<"\n";
    }

    ~ComplaintList(){
        while(head){
            SNode* t=head;
            head=head->next;
            delete t;
        }
    }
};

// [CO3] Doubly linked list: bidirectional maintenance logs
struct DNode {
    string log;
    DNode *prev,*next;
    DNode(string s):log(s),prev(nullptr),next(nullptr){}
};

class MaintenanceLog {
    DNode *head=nullptr,*tail=nullptr;
public:
    void add(string s){
        DNode* n=new DNode(s);
        if(!head)head=tail=n;
        else{
            tail->next=n;
            n->prev=tail;
            tail=n;
        }
    }

    void forward(){
        for(DNode*p=head;p;p=p->next)
            cout<<p->log<<"\n";
    }

    void backward(){
        for(DNode*p=tail;p;p=p->prev)
            cout<<p->log<<"\n";
    }

    ~MaintenanceLog(){
        while(head){
            DNode*t=head;
            head=head->next;
            delete t;
        }
    }
};

// [CO4] Circular queue for priority-based ticket dispatch
class CircularQueue {
    vector<Asset> q;
    int front=0,rear=0,count=0;
    int capacity=20;
public:
    CircularQueue():q(20){}

    bool enqueue(Asset a){
        if(count==capacity)return false;
        q[rear]=a;
        rear=(rear+1)%capacity;
        count++;
        return true;
    }

    bool dequeue(Asset& a){
        if(count==0)return false;
        a=q[front];
        front=(front+1)%capacity;
        count--;
        return true;
    }

    void dispatch(){
        vector<Asset> items;
        Asset a;
        while(dequeue(a))items.push_back(a);

        // [CO5] QuickSort-based severity ordering
        sort(items.begin(),items.end(),
            [](const Asset&x,const Asset&y){
                return x.severity>y.severity;
            });

        cout<<"\n--- PRIORITY DISPATCH ---\n";
        for(auto&x:items){
            cout<<x.id<<" | "<<x.issue
                <<" | Severity: "<<x.severity<<"\n";
            enqueue(x);
        }
    }
};

// [CO4] Stack for one-click undo
struct Action {
    int index;
    Asset previous;
};

stack<Action> undoStack;

// [CO6] Hash table using separate chaining
class AssetHash {
    vector<vector<Asset>> table;
    int hashFunction(string id){
        int h=0;
        for(char c:id)h=(h*31+c)%HASH_SIZE;
        return h;
    }
public:
    AssetHash():table(HASH_SIZE){}

    void insert(Asset a){
        int h=hashFunction(a.id);
        for(auto&x:table[h]){
            if(x.id==a.id){x=a;return;}
        }
        table[h].push_back(a);
    }

    bool search(string id){
        int h=hashFunction(id);
        for(auto&x:table[h]){
            if(x.id==id){
                cout<<"Found: "<<x.id<<" "<<x.status
                    <<" "<<x.issue<<"\n";
                return true;
            }
        }
        cout<<"Asset not found.\n";
        return false;
    }
};

// [CO5] Linear search
int linearSearch(const vector<Asset>&a,string id){
    for(int i=0;i<(int)a.size();i++)
        if(a[i].id==id)return i;
    return -1;
}

// [CO5] Binary search on sorted asset IDs
int binarySearch(vector<Asset>a,string id){
    sort(a.begin(),a.end(),
        [](Asset x,Asset y){return x.id<y.id;});

    int l=0,r=(int)a.size()-1;
    while(l<=r){
        int m=(l+r)/2;
        if(a[m].id==id)return m;
        if(a[m].id<id)l=m+1;
        else r=m-1;
    }
    return -1;
}

// [CO5] QuickSort implementation
void quickSort(vector<Asset>&a,int low,int high){
    if(low>=high)return;
    int i=low,j=high;
    int pivot=a[(low+high)/2].severity;
    while(i<=j){
        while(a[i].severity>pivot)i++;
        while(a[j].severity<pivot)j--;
        if(i<=j){
            swap(a[i],a[j]);
            i++;j--;
        }
    }
    if(low<j)quickSort(a,low,j);
    if(i<high)quickSort(a,i,high);
}

int main(){
    vector<Asset> assets;
    Asset grid[ROWS][COLS];

    ComplaintList complaints;
    MaintenanceLog logs;
    CircularQueue tickets;
    AssetHash hashTable;

    // [CO2] 2D array: 3x4 laboratory seating grid
    cout<<"====================================\n";
    cout<<"       INVENTRA - DS MODULE\n";
    cout<<"====================================\n";

    for(int r=0;r<ROWS;r++){
        for(int c=0;c<COLS;c++){
            int n=r*COLS+c+1;
            grid[r][c]={
                "PC-"+to_string(n),
                "WORKING","None",1
            };
            assets.push_back(grid[r][c]);
            hashTable.insert(grid[r][c]);
        }
    }

    int choice;

    do{
        cout<<"\n1. Display Lab Grid";
        cout<<"\n2. Update Asset";
        cout<<"\n3. Undo Last Update";
        cout<<"\n4. Add Complaint";
        cout<<"\n5. Display Complaint History";
        cout<<"\n6. Add Maintenance Log";
        cout<<"\n7. View Logs Forward";
        cout<<"\n8. View Logs Backward";
        cout<<"\n9. Add Priority Ticket";
        cout<<"\n10. Dispatch Tickets";
        cout<<"\n11. Linear Search";
        cout<<"\n12. Binary Search";
        cout<<"\n13. QuickSort by Severity";
        cout<<"\n14. Hash Table Search";
        cout<<"\n15. Exit";
        cout<<"\nChoice: ";

        cin>>choice;
        cin.ignore();

        switch(choice){

        case 1:
            for(int r=0;r<ROWS;r++){
                for(int c=0;c<COLS;c++)
                    cout<<setw(12)<<grid[r][c].id;
                cout<<"\n";
            }
            break;

        case 2:{
            string id,status,issue;
            cout<<"Asset ID: ";getline(cin,id);
            int i=linearSearch(assets,id);
            if(i<0){cout<<"Not found.\n";break;}

            undoStack.push({i,assets[i]});
            cout<<"New status: ";getline(cin,status);
            cout<<"Issue: ";getline(cin,issue);

            assets[i].status=status;
            assets[i].issue=issue;

            grid[i/4][i%4]=assets[i];
            hashTable.insert(assets[i]);

            logs.add(id+" updated to "+status);
            cout<<"Updated successfully.\n";
            break;
        }

        case 3:
            if(!undoStack.empty()){
                Action a=undoStack.top();
                undoStack.pop();
                assets[a.index]=a.previous;
                grid[a.index/4][a.index%4]=a.previous;
                hashTable.insert(a.previous);
                cout<<"Undo successful.\n";
            }else cout<<"Nothing to undo.\n";
            break;

        case 4:{
            string s;
            cout<<"Complaint: ";getline(cin,s);
            complaints.add(s);
            cout<<"Complaint added.\n";
            break;
        }

        case 5:
            complaints.display();
            break;

        case 6:{
            string s;
            cout<<"Maintenance log: ";getline(cin,s);
            logs.add(s);
            break;
        }

        case 7:
            logs.forward();
            break;

        case 8:
            logs.backward();
            break;

        case 9:{
            string id,issue;
            int severity;
            cout<<"Asset ID: ";getline(cin,id);
            cout<<"Issue: ";getline(cin,issue);
            cout<<"Severity (1-5): ";cin>>severity;
            cin.ignore();
            if(severity<1||severity>5){
                cout<<"Invalid severity.\n";break;
            }
            tickets.enqueue({id,"FAULTY",issue,severity});
            cout<<"Ticket queued.\n";
            break;
        }

        case 10:
            tickets.dispatch();
            break;

        case 11:{
            string id;
            cout<<"Search ID: ";getline(cin,id);
            int i=linearSearch(assets,id);
            cout<<(i>=0?"Found":"Not found")<<"\n";
            break;
        }

        case 12:{
            string id;
            cout<<"Search ID: ";getline(cin,id);
            int i=binarySearch(assets,id);
            cout<<(i>=0?"Found":"Not found")<<"\n";
            break;
        }

        case 13:
            quickSort(assets,0,(int)assets.size()-1);
            for(auto&a:assets)
                cout<<a.id<<" Severity: "<<a.severity<<"\n";
            break;

        case 14:{
            string id;
            cout<<"Barcode / Asset ID: ";getline(cin,id);
            hashTable.search(id);
            break;
        }

        case 15:
            cout<<"Exiting Inventra DS Module.\n";
            break;

        default:
            cout<<"Invalid choice.\n";
        }

    }while(choice!=15);

    return 0;
}
